package com.kuaishou.intelligentanalysisplatform.application.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.application.node.NodeExecuteDispatcher;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorInfoDTO;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ErrorHandlerNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowEdgeDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowNodeDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowRunLogDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowRunRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowRunResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import com.kuaishou.intelligentanalysisplatform.domain.workflow.WorkflowRunLog;
import com.kuaishou.intelligentanalysisplatform.domain.workflow.WorkflowRunLogRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * DAG-based parallel workflow executor with conditional routing and error recovery support.
 *
 * <p>改造要点：
 * <ol>
 *   <li>CONDITION 节点执行后，根据 "_branch" 输出预置非匹配分支的目标节点为 SKIPPED。</li>
 *   <li>SKIPPED 状态向下传播：上游 SKIPPED 则当前节点也 SKIPPED。</li>
 *   <li>ERROR_HANDLER 节点对其 guardedNodeId 的 FAILED 状态免疫，执行 retry/fallback 逻辑。</li>
 *   <li>向后兼容：condition = null 的边与现有行为完全一致。</li>
 *   <li>Phase 8：执行完成后将 run log 写入 workflow_run_log 表，并上报 Micrometer 指标。</li>
 * </ol>
 */
@Component
public class WorkflowDagExecutor {

    private static final int    WORKFLOW_TIMEOUT_MINUTES  = 10;
    private static final long   SLOW_WORKFLOW_THRESHOLD_MS = 60_000;
    private static final String CONDITION_NODE_TYPE       = "condition";
    private static final String ERROR_HANDLER_NODE_TYPE   = "error_handler";
    private static final Logger TASK_EXECUTION = LoggerFactory.getLogger("TASK_EXECUTION");

    private final NodeExecuteDispatcher      nodeExecuteDispatcher;
    private final ObjectMapper               objectMapper;
    private final WorkflowRunLogRepository   workflowRunLogRepository;
    private final MeterRegistry              meterRegistry;

    public WorkflowDagExecutor(NodeExecuteDispatcher nodeExecuteDispatcher,
                                ObjectMapper objectMapper,
                                WorkflowRunLogRepository workflowRunLogRepository,
                                MeterRegistry meterRegistry) {
        this.nodeExecuteDispatcher    = nodeExecuteDispatcher;
        this.objectMapper             = objectMapper;
        this.workflowRunLogRepository = workflowRunLogRepository;
        this.meterRegistry            = meterRegistry;
    }

    // ─── 条件边数据结构 ────────────────────────────────────────────────────────
    record ConditionalEdge(String source, String target, String condition) {}

    // ──────────────────────────────────────────────────────────────────────────

    public WorkflowRunResultDTO execute(WorkflowRunRequestDTO request, String runId) {
        List<WorkflowNodeDTO> nodes = request.getNodes();
        List<WorkflowEdgeDTO> edges = request.getEdges() != null ? request.getEdges() : List.of();

        String tenantId = request.getContext() != null ? request.getContext().getTenantId() : "unknown";
        String userId   = request.getContext() != null ? request.getContext().getUserId()   : null;
        long   startedAt = System.currentTimeMillis();

        // ── Phase 8: 插入 RUNNING 记录 ──
        workflowRunLogRepository.insert(WorkflowRunLog.builder()
                .runId(runId)
                .workflowId(request.getWorkflowId())
                .tenantId(tenantId)
                .triggerType("MANUAL")
                .status("RUNNING")
                .nodeCount(nodes.size())
                .startedAt(startedAt)
                .createdBy(userId)
                .build());

        // nodeId → node
        Map<String, WorkflowNodeDTO> nodeMap = new LinkedHashMap<>();
        // nodeId → 直接上游 nodeId 集合
        Map<String, Set<String>> dependsOn = new HashMap<>();

        for (WorkflowNodeDTO node : nodes) {
            nodeMap.put(node.getNodeId(), node);
            dependsOn.put(node.getNodeId(), new HashSet<>());
        }

        // 条件边索引：sourceNodeId → 该节点发出的所有条件边列表
        Map<String, List<ConditionalEdge>> conditionalEdgesMap = new HashMap<>();

        for (WorkflowEdgeDTO edge : edges) {
            String src = edge.getSource();
            String tgt = edge.getTarget();
            if (src == null || tgt == null || !nodeMap.containsKey(tgt)) {
                continue;
            }
            dependsOn.get(tgt).add(src);

            if (edge.getCondition() != null) {
                conditionalEdgesMap
                        .computeIfAbsent(src, k -> new ArrayList<>())
                        .add(new ConditionalEdge(src, tgt, edge.getCondition()));
            }
        }

        // Thread-safe state
        ConcurrentHashMap<String, StandardResultDTO> completedResults = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, NodeResultDTO>     nodeResultsMap   = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, CompletableFuture<NodeResultDTO>> futures = new ConcurrentHashMap<>();
        // Phase 8: 节点级 trace 收集
        ConcurrentHashMap<String, WorkflowRunLogDTO.NodeTraceDTO> nodeTraces = new ConcurrentHashMap<>();

        for (WorkflowNodeDTO node : nodes) {
            futures.put(node.getNodeId(), new CompletableFuture<>());
        }

        // Wire up each node to execute after its deps are done
        for (WorkflowNodeDTO node : nodes) {
            Set<String> deps = dependsOn.get(node.getNodeId());

            CompletableFuture<Void> trigger;
            if (deps.isEmpty()) {
                trigger = CompletableFuture.completedFuture(null);
            } else {
                @SuppressWarnings("unchecked")
                CompletableFuture<NodeResultDTO>[] depFutures = deps.stream()
                        .map(futures::get)
                        .filter(Objects::nonNull)
                        .toArray(CompletableFuture[]::new);
                trigger = CompletableFuture.allOf(depFutures);
            }

            final WorkflowNodeDTO finalNode = node;
            trigger.thenRunAsync(
                    () -> executeNode(finalNode, deps, futures, completedResults, nodeResultsMap,
                                      request, runId, nodes, conditionalEdgesMap, nodeTraces),
                    ForkJoinPool.commonPool()
            ).exceptionally(ex -> {
                completeWithError(finalNode, futures, nodeResultsMap, ex.getMessage());
                return null;
            });
        }

        // Wait for all nodes
        try {
            CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]))
                    .get(WORKFLOW_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            futures.values().forEach(f -> f.cancel(true));
            throw new BaseBusinessException(ErrorCode.INTERNAL_ERROR, "workflow execution timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            // individual node errors are captured inline
        }

        WorkflowRunResultDTO result = buildResult(request.getWorkflowId(), nodes, nodeResultsMap);

        // ── Phase 8: 完成后写入 DB ──
        long elapsedMs  = System.currentTimeMillis() - startedAt;
        long finishedAt = System.currentTimeMillis();
        String finalStatus = result.getStatus() != null ? result.getStatus().name() : "SUCCEEDED";
        String traceJson   = buildNodeTraceJson(nodes, nodeTraces, nodeResultsMap);

        try {
            workflowRunLogRepository.complete(runId, finalStatus, elapsedMs, finishedAt, traceJson);
        } catch (Exception ex) {
            TASK_EXECUTION.warn("{\"event\":\"run_log_complete_failed\",\"runId\":\"{}\",\"error\":\"{}\"}", runId, ex.getMessage());
        }

        // ── Phase 8: Micrometer 指标 ──
        meterRegistry.counter("workflow.run.count",
                "status", finalStatus,
                "workflowId", request.getWorkflowId()
        ).increment();
        meterRegistry.timer("workflow.run.duration",
                "workflowId", request.getWorkflowId()
        ).record(elapsedMs, TimeUnit.MILLISECONDS);

        // ── Phase 8: 慢执行告警 ──
        if (elapsedMs > SLOW_WORKFLOW_THRESHOLD_MS) {
            TASK_EXECUTION.warn("{\"event\":\"slow_workflow\",\"workflowId\":\"{}\",\"runId\":\"{}\",\"elapsedMs\":{}}",
                    request.getWorkflowId(), runId, elapsedMs);
        }

        return result;
    }

    // ─── 节点执行 ─────────────────────────────────────────────────────────────

    private void executeNode(WorkflowNodeDTO node,
                             Set<String> deps,
                             ConcurrentHashMap<String, CompletableFuture<NodeResultDTO>> futures,
                             ConcurrentHashMap<String, StandardResultDTO> completedResults,
                             ConcurrentHashMap<String, NodeResultDTO> nodeResultsMap,
                             WorkflowRunRequestDTO request,
                             String runId,
                             List<WorkflowNodeDTO> allNodes,
                             Map<String, List<ConditionalEdge>> conditionalEdgesMap,
                             ConcurrentHashMap<String, WorkflowRunLogDTO.NodeTraceDTO> nodeTraces) {

        // 已被条件路由预置为 SKIPPED，跳过重复执行
        if (futures.get(node.getNodeId()).isDone()) {
            return;
        }

        // 检查上游状态，决定是否 skip 当前节点
        for (String depId : deps) {
            CompletableFuture<NodeResultDTO> depFuture = futures.get(depId);
            if (depFuture == null || !depFuture.isDone()) continue;

            NodeResultDTO depResult;
            try {
                depResult = depFuture.get();
            } catch (Exception ignored) {
                completeWithSkip(node, futures, nodeResultsMap,
                        "upstream [" + depId + "] threw exception, skipping");
                return;
            }
            if (depResult == null) continue;

            ExecutionStatus depStatus = depResult.getStatus();
            if (depStatus == ExecutionStatus.FAILED || depStatus == ExecutionStatus.SKIPPED) {
                // ErrorHandler 节点对其 guardedNodeId 的 FAILED 免疫
                if (ERROR_HANDLER_NODE_TYPE.equals(node.getNodeType())
                        && depStatus == ExecutionStatus.FAILED
                        && node.getConfig() instanceof ErrorHandlerNodeConfigDTO ehConfig
                        && depId.equals(ehConfig.getGuardedNodeId())) {
                    continue; // 允许 ErrorHandler 执行
                }
                completeWithSkip(node, futures, nodeResultsMap,
                        "upstream [" + depId + "] is " + depStatus + ", skipping");
                return;
            }
        }

        // 快照上游结果
        Map<String, StandardResultDTO> snapshot = new LinkedHashMap<>(completedResults);

        NodeExecuteContextDTO context = NodeExecuteContextDTO.builder()
                .workflowId(request.getWorkflowId())
                .runId(runId)
                .nodeId(node.getNodeId())
                .upstreamResults(snapshot)
                .requestContext(request.getContext())
                .allNodes(allNodes)
                .build();

        long nodeStart = System.currentTimeMillis();
        try {
            // 对于 CONDITION 节点，先激活条件边（预置另一分支为 SKIPPED），再完成自身 future
            NodeResultDTO result = nodeExecuteDispatcher.dispatch(node, context);
            nodeResultsMap.put(node.getNodeId(), result);
            if (result.getResult() != null) {
                completedResults.put(node.getNodeId(), result.getResult());
            }

            // CONDITION 节点：在完成自身 future 之前，预置非匹配分支为 SKIPPED
            if (CONDITION_NODE_TYPE.equals(node.getNodeType())) {
                activateConditionalEdges(node.getNodeId(), result, conditionalEdgesMap, futures, nodeResultsMap);
            }

            futures.get(node.getNodeId()).complete(result);

            long elapsed = System.currentTimeMillis() - nodeStart;
            logNodeCompletion(node.getNodeId(), node.getNodeType(), result.getStatus(), elapsed, null, result, nodeTraces);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - nodeStart;
            logNodeCompletion(node.getNodeId(), node.getNodeType(), ExecutionStatus.FAILED, elapsed, e.getMessage(), null, nodeTraces);
            completeWithError(node, futures, nodeResultsMap, e.getMessage());
        }
    }

    // ─── 条件路由 ─────────────────────────────────────────────────────────────

    private void activateConditionalEdges(String conditionNodeId,
                                           NodeResultDTO result,
                                           Map<String, List<ConditionalEdge>> conditionalEdgesMap,
                                           ConcurrentHashMap<String, CompletableFuture<NodeResultDTO>> futures,
                                           ConcurrentHashMap<String, NodeResultDTO> nodeResultsMap) {
        List<ConditionalEdge> edges = conditionalEdgesMap.get(conditionNodeId);
        if (edges == null || edges.isEmpty()) return;

        String branch = extractBranch(result);
        if (branch == null) return;

        for (ConditionalEdge edge : edges) {
            if (edge.condition() != null && !edge.condition().equals(branch)) {
                preCompleteWithSkip(edge.target(), futures, nodeResultsMap,
                        "condition node [" + conditionNodeId + "] branch=" + branch
                                + ", edge.condition=" + edge.condition() + " → SKIPPED");
            }
        }
    }

    private String extractBranch(NodeResultDTO result) {
        if (result == null || result.getResult() == null) return null;
        Map<String, Object> vars = result.getResult().getVariables();
        if (vars == null) return null;
        Object branch = vars.get("_branch");
        return branch != null ? branch.toString() : null;
    }

    // ─── 辅助方法 ─────────────────────────────────────────────────────────────

    private void preCompleteWithSkip(String nodeId,
                                      ConcurrentHashMap<String, CompletableFuture<NodeResultDTO>> futures,
                                      ConcurrentHashMap<String, NodeResultDTO> nodeResultsMap,
                                      String reason) {
        NodeResultDTO skipped = NodeResultDTO.builder()
                .nodeId(nodeId)
                .status(ExecutionStatus.SKIPPED)
                .error(ErrorInfoDTO.builder()
                        .code("SKIPPED")
                        .message(reason)
                        .retryable(false)
                        .build())
                .build();
        nodeResultsMap.put(nodeId, skipped);
        CompletableFuture<NodeResultDTO> future = futures.get(nodeId);
        if (future != null) {
            future.complete(skipped);
        }
    }

    private void completeWithSkip(WorkflowNodeDTO node,
                                   ConcurrentHashMap<String, CompletableFuture<NodeResultDTO>> futures,
                                   ConcurrentHashMap<String, NodeResultDTO> nodeResultsMap,
                                   String reason) {
        NodeResultDTO skipped = NodeResultDTO.builder()
                .nodeId(node.getNodeId())
                .nodeType(node.getNodeType())
                .status(ExecutionStatus.SKIPPED)
                .error(ErrorInfoDTO.builder()
                        .code("SKIPPED")
                        .message(reason != null ? reason : "upstream skipped or failed")
                        .retryable(false)
                        .build())
                .build();
        nodeResultsMap.put(node.getNodeId(), skipped);
        CompletableFuture<NodeResultDTO> future = futures.get(node.getNodeId());
        if (future != null) {
            future.complete(skipped);
        }
    }

    private void completeWithError(WorkflowNodeDTO node,
                                   ConcurrentHashMap<String, CompletableFuture<NodeResultDTO>> futures,
                                   ConcurrentHashMap<String, NodeResultDTO> nodeResultsMap,
                                   String message) {
        NodeResultDTO failed = NodeResultDTO.builder()
                .nodeId(node.getNodeId())
                .nodeType(node.getNodeType())
                .status(ExecutionStatus.FAILED)
                .error(ErrorInfoDTO.builder()
                        .code(ErrorCode.INTERNAL_ERROR.getCode())
                        .message(message != null ? message : "node execution failed")
                        .retryable(false)
                        .build())
                .build();
        nodeResultsMap.put(node.getNodeId(), failed);
        futures.get(node.getNodeId()).complete(failed);
    }

    // ─── 日志 + Phase 8 Trace 收集 ────────────────────────────────────────────

    private void logNodeCompletion(String nodeId, String nodeType, ExecutionStatus status,
                                    long elapsedMs, String error,
                                    NodeResultDTO nodeResult,
                                    ConcurrentHashMap<String, WorkflowRunLogDTO.NodeTraceDTO> nodeTraces) {
        Map<String, Object> logEntry = new LinkedHashMap<>();
        logEntry.put("event", "dag_node_completed");
        logEntry.put("nodeId", nodeId);
        logEntry.put("nodeType", nodeType);
        logEntry.put("status", status != null ? status.name() : "UNKNOWN");
        logEntry.put("elapsedMs", elapsedMs);
        if (error != null) {
            logEntry.put("error", error);
        }
        try {
            if (status == ExecutionStatus.FAILED) {
                TASK_EXECUTION.warn(objectMapper.writeValueAsString(logEntry));
            } else {
                TASK_EXECUTION.info(objectMapper.writeValueAsString(logEntry));
            }
        } catch (JsonProcessingException e) {
            TASK_EXECUTION.info("{\"event\":\"dag_node_completed\",\"nodeId\":\"" + nodeId
                    + "\",\"status\":\"" + status + "\"}");
        }

        // Phase 8: 节点级 Micrometer Timer
        if (status != null) {
            meterRegistry.timer("node.execute.duration",
                    "nodeType", nodeType != null ? nodeType : "unknown",
                    "status", status.name()
            ).record(elapsedMs, TimeUnit.MILLISECONDS);
        }

        // Phase 8: 更新内存 trace 列表
        WorkflowRunLogDTO.NodeTraceDTO.NodeTraceDTOBuilder traceBuilder = WorkflowRunLogDTO.NodeTraceDTO.builder()
                .nodeId(nodeId)
                .nodeType(nodeType)
                .status(status != null ? status.name() : "UNKNOWN")
                .elapsedMs(elapsedMs)
                .error(error);

        if (nodeResult != null) {
            if (nodeResult.getMeta() != null) {
                traceBuilder.cached(nodeResult.getMeta().getCached());
                traceBuilder.pushdown(nodeResult.getMeta().getPushdownApplied());
            }
            if (nodeResult.getResult() != null && nodeResult.getResult().getDataset() != null
                    && nodeResult.getResult().getDataset().getRows() != null) {
                traceBuilder.rowCount(nodeResult.getResult().getDataset().getRows().size());
            }
        }
        nodeTraces.put(nodeId, traceBuilder.build());
    }

    // ─── 结果汇总 ─────────────────────────────────────────────────────────────

    private WorkflowRunResultDTO buildResult(String workflowId,
                                              List<WorkflowNodeDTO> nodes,
                                              ConcurrentHashMap<String, NodeResultDTO> nodeResultsMap) {
        List<NodeResultDTO> orderedResults = new ArrayList<>();
        StandardResultDTO finalResult = null;
        ExecutionStatus workflowStatus = ExecutionStatus.SUCCEEDED;

        for (WorkflowNodeDTO node : nodes) {
            NodeResultDTO r = nodeResultsMap.get(node.getNodeId());
            if (r == null) continue;
            orderedResults.add(r);
            if (r.getStatus() == ExecutionStatus.FAILED) {
                workflowStatus = ExecutionStatus.FAILED;
            }
            if (r.getResult() != null) {
                finalResult = r.getResult();
            }
        }

        return WorkflowRunResultDTO.builder()
                .workflowId(workflowId)
                .status(workflowStatus)
                .nodeResults(orderedResults)
                .finalResult(finalResult)
                .build();
    }

    // ─── Phase 8: Node trace JSON 序列化 ─────────────────────────────────────

    private String buildNodeTraceJson(List<WorkflowNodeDTO> nodes,
                                       ConcurrentHashMap<String, WorkflowRunLogDTO.NodeTraceDTO> nodeTraces,
                                       ConcurrentHashMap<String, NodeResultDTO> nodeResultsMap) {
        List<WorkflowRunLogDTO.NodeTraceDTO> orderedTraces = new ArrayList<>();
        for (WorkflowNodeDTO node : nodes) {
            WorkflowRunLogDTO.NodeTraceDTO trace = nodeTraces.get(node.getNodeId());
            if (trace == null) {
                // SKIPPED 节点通过 completeWithSkip 跳过了 logNodeCompletion，补全其 trace
                NodeResultDTO r = nodeResultsMap.get(node.getNodeId());
                if (r != null) {
                    trace = WorkflowRunLogDTO.NodeTraceDTO.builder()
                            .nodeId(node.getNodeId())
                            .nodeType(node.getNodeType())
                            .status(r.getStatus() != null ? r.getStatus().name() : "UNKNOWN")
                            .error(r.getError() != null ? r.getError().getMessage() : null)
                            .build();
                }
            }
            if (trace != null) {
                orderedTraces.add(trace);
            }
        }
        try {
            return objectMapper.writeValueAsString(orderedTraces);
        } catch (JsonProcessingException e) {
            TASK_EXECUTION.warn("Failed to serialize node traces: {}", e.getMessage());
            return "[]";
        }
    }
}
