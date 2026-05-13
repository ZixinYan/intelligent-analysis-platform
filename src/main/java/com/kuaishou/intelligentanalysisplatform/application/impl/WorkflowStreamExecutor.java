package com.kuaishou.intelligentanalysisplatform.application.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.application.node.NodeExecuteDispatcher;
import com.kuaishou.intelligentanalysisplatform.domain.workflow.WorkflowRunLog;
import com.kuaishou.intelligentanalysisplatform.domain.workflow.WorkflowRunLogRepository;
import io.micrometer.core.instrument.MeterRegistry;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorInfoDTO;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ResultKind;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeDebugRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowEdgeDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowNodeDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowRunRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.stream.WorkflowStreamEvent;
import com.kuaishou.intelligentanalysisplatform.contract.schema.stream.WorkflowStreamEvent.NodeProgressEvent;
import com.kuaishou.intelligentanalysisplatform.contract.schema.stream.WorkflowStreamEvent.NodeResultEvent;
import com.kuaishou.intelligentanalysisplatform.contract.schema.stream.WorkflowStreamEvent.NodeStartEvent;
import com.kuaishou.intelligentanalysisplatform.contract.schema.stream.WorkflowStreamEvent.WorkflowDoneEvent;
import com.kuaishou.intelligentanalysisplatform.contract.schema.stream.WorkflowStreamEvent.WorkflowErrorEvent;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 流式 DAG 执行器。
 * 在 WorkflowDagExecutor 的并行 DAG 逻辑基础上，每个执行阶段通过 SseEmitter 推送对应的事件：
 * <pre>
 *   node_start → [node_progress × N（大数据集）] → node_result
 *                                                ↘
 *                                              workflow_done | workflow_error
 * </pre>
 */
@Component
public class WorkflowStreamExecutor {

    private static final Logger log = LoggerFactory.getLogger(WorkflowStreamExecutor.class);

    /** 超过此行数时启用分块推送 */
    static final int LARGE_DATASET_THRESHOLD = 1000;
    /** 每个 node_progress 事件携带的最大行数 */
    static final int DEFAULT_CHUNK_SIZE = 500;

    private static final int    WORKFLOW_TIMEOUT_MINUTES = 10;
    private static final String CONDITION_NODE_TYPE      = "condition";

    // ─── 条件边数据结构 ────────────────────────────────────────────────────────
    record ConditionalEdge(String source, String target, String condition) {}

    private final NodeExecuteDispatcher nodeExecuteDispatcher;
    private final ObjectMapper objectMapper;
    private final WorkflowRunLogRepository workflowRunLogRepository;
    private final MeterRegistry meterRegistry;

    public WorkflowStreamExecutor(NodeExecuteDispatcher nodeExecuteDispatcher,
                                   ObjectMapper objectMapper,
                                   WorkflowRunLogRepository workflowRunLogRepository,
                                   MeterRegistry meterRegistry) {
        this.nodeExecuteDispatcher = nodeExecuteDispatcher;
        this.objectMapper = objectMapper;
        this.workflowRunLogRepository = workflowRunLogRepository;
        this.meterRegistry = meterRegistry;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * 执行多节点 DAG 工作流，通过 SseEmitter 推送进度。
     * 立即返回（在独立线程中异步执行）。
     */
    public void executeWorkflow(WorkflowRunRequestDTO request, SseEmitter emitter) {
        String runId = UUID.randomUUID().toString();
        ForkJoinPool.commonPool().execute(() -> doExecuteWorkflow(request, runId, emitter));
    }

    /**
     * 执行单节点调试，通过 SseEmitter 推送结果（大结果集分块）。
     */
    public void executeNode(NodeDebugRequestDTO request, SseEmitter emitter) {
        String runId = UUID.randomUUID().toString();
        ForkJoinPool.commonPool().execute(() -> doExecuteNode(request, runId, emitter));
    }

    // -------------------------------------------------------------------------
    // Workflow execution
    // -------------------------------------------------------------------------

    private void doExecuteWorkflow(WorkflowRunRequestDTO request, String runId, SseEmitter emitter) {
        long workflowStart = System.currentTimeMillis();
        List<WorkflowNodeDTO> nodes = request.getNodes();
        List<WorkflowEdgeDTO> edges = request.getEdges() != null ? request.getEdges() : List.of();

        String tenantId = request.getContext() != null ? request.getContext().getTenantId() : "unknown";
        String userId   = request.getContext() != null ? request.getContext().getUserId()   : null;

        // 执行开始时写入 RUNNING 记录
        try {
            workflowRunLogRepository.insert(WorkflowRunLog.builder()
                    .runId(runId)
                    .workflowId(request.getWorkflowId())
                    .tenantId(tenantId)
                    .triggerType("STREAM")
                    .status("RUNNING")
                    .nodeCount(nodes.size())
                    .startedAt(workflowStart)
                    .createdBy(userId)
                    .build());
        } catch (Exception ex) {
            log.warn("Failed to insert stream run log for runId={}: {}", runId, ex.getMessage());
        }

        // Build dependency graph (same logic as WorkflowDagExecutor)
        Map<String, WorkflowNodeDTO> nodeMap = new LinkedHashMap<>();
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

        ConcurrentHashMap<String, StandardResultDTO> completedResults = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, NodeResultDTO> nodeResultsMap = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, CompletableFuture<NodeResultDTO>> futures = new ConcurrentHashMap<>();

        for (WorkflowNodeDTO node : nodes) {
            futures.put(node.getNodeId(), new CompletableFuture<>());
        }

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
            final Map<String, List<ConditionalEdge>> finalConditionalEdgesMap = conditionalEdgesMap;
            trigger.thenRunAsync(
                    () -> executeStreamNode(finalNode, deps, futures, completedResults,
                            nodeResultsMap, request, runId, emitter, finalConditionalEdgesMap),
                    ForkJoinPool.commonPool()
            ).exceptionally(ex -> {
                completeNodeWithError(finalNode, futures, nodeResultsMap, ex.getMessage(), runId, emitter);
                return null;
            });
        }

        // Wait for all nodes
        try {
            CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]))
                    .get(WORKFLOW_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            futures.values().forEach(f -> f.cancel(true));
            long elapsedOnTimeout = System.currentTimeMillis() - workflowStart;
            completeRunLog(runId, request.getWorkflowId(), "FAILED", elapsedOnTimeout, null);
            sendEvent(emitter, new WorkflowErrorEvent(runId, request.getWorkflowId(),
                    ErrorInfoDTO.builder()
                            .code(ErrorCode.INTERNAL_ERROR.getCode())
                            .message("workflow execution timed out")
                            .retryable(true)
                            .build()));
            emitter.complete();
            return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            // individual node errors captured inline
        }

        // Determine overall status
        boolean anyFailed = nodeResultsMap.values().stream()
                .anyMatch(r -> r.getStatus() == ExecutionStatus.FAILED);
        String status = anyFailed ? "FAILED" : "SUCCEEDED";
        long elapsed = System.currentTimeMillis() - workflowStart;

        // 写入终态 run log
        String traceJson = buildNodeTraceJson(nodes, nodeResultsMap);
        completeRunLog(runId, request.getWorkflowId(), status, elapsed, traceJson);

        // 上报 Micrometer 指标
        meterRegistry.counter("workflow.run.count",
                "status", status,
                "workflowId", request.getWorkflowId()
        ).increment();
        meterRegistry.timer("workflow.run.duration",
                "workflowId", request.getWorkflowId()
        ).record(elapsed, TimeUnit.MILLISECONDS);

        if (elapsed > 60_000) {
            log.warn("slow_stream_workflow workflowId={} runId={} elapsedMs={}",
                    request.getWorkflowId(), runId, elapsed);
        }

        sendEvent(emitter, new WorkflowDoneEvent(runId, request.getWorkflowId(), status, elapsed));
        emitter.complete();
    }

    private void executeStreamNode(WorkflowNodeDTO node,
                                   Set<String> deps,
                                   ConcurrentHashMap<String, CompletableFuture<NodeResultDTO>> futures,
                                   ConcurrentHashMap<String, StandardResultDTO> completedResults,
                                   ConcurrentHashMap<String, NodeResultDTO> nodeResultsMap,
                                   WorkflowRunRequestDTO request,
                                   String runId,
                                   SseEmitter emitter,
                                   Map<String, List<ConditionalEdge>> conditionalEdgesMap) {
        // 已被条件路由预置为 SKIPPED，跳过重复执行
        if (futures.get(node.getNodeId()).isDone()) {
            return;
        }

        // 检查上游状态：FAILED 或 SKIPPED 均导致当前节点 SKIPPED
        for (String depId : deps) {
            CompletableFuture<NodeResultDTO> depFuture = futures.get(depId);
            if (depFuture == null || !depFuture.isDone()) continue;

            NodeResultDTO depResult;
            try {
                depResult = depFuture.get();
            } catch (Exception ignored) {
                completeNodeWithSkip(node, futures, nodeResultsMap,
                        "upstream node [" + depId + "] threw exception", runId, emitter);
                return;
            }
            if (depResult == null) continue;

            ExecutionStatus depStatus = depResult.getStatus();
            if (depStatus == ExecutionStatus.FAILED || depStatus == ExecutionStatus.SKIPPED) {
                completeNodeWithSkip(node, futures, nodeResultsMap,
                        "upstream node [" + depId + "] is " + depStatus + ", skipping", runId, emitter);
                return;
            }
        }

        // Snapshot completed results for deterministic context
        Map<String, StandardResultDTO> snapshot = new LinkedHashMap<>(completedResults);
        NodeExecuteContextDTO context = NodeExecuteContextDTO.builder()
                .workflowId(request.getWorkflowId())
                .runId(runId)
                .nodeId(node.getNodeId())
                .upstreamResults(snapshot)
                .requestContext(request.getContext())
                .allNodes(request.getNodes())
                .build();

        // Push node_start
        sendEvent(emitter, new NodeStartEvent(runId, node.getNodeId(),
                node.getNodeType(), System.currentTimeMillis()));

        long nodeStart = System.currentTimeMillis();
        try {
            NodeResultDTO result = nodeExecuteDispatcher.dispatch(node, context);

            // CONDITION 节点：预置非匹配分支为 SKIPPED，并推送 SKIPPED 事件
            if (CONDITION_NODE_TYPE.equals(node.getNodeType())) {
                activateConditionalEdges(node.getNodeId(), result, conditionalEdgesMap,
                        futures, nodeResultsMap, runId, emitter);
            }

            // Chunk large datasets before pushing node_result
            StandardResultDTO streamResult = chunkAndStreamDataset(
                    result, node.getNodeId(), runId, DEFAULT_CHUNK_SIZE, emitter);

            nodeResultsMap.put(node.getNodeId(), result);
            if (result.getResult() != null) {
                completedResults.put(node.getNodeId(), result.getResult());
            }
            futures.get(node.getNodeId()).complete(result);

            // Push node_result (with rows stripped if already streamed via node_progress)
            sendEvent(emitter, new NodeResultEvent(
                    runId,
                    node.getNodeId(),
                    result.getStatus() != null ? result.getStatus().name() : "SUCCEEDED",
                    streamResult,
                    result.getMeta()));

        } catch (Exception e) {
            long nodeElapsed = System.currentTimeMillis() - nodeStart;
            log.warn("node {} failed after {}ms: {}", node.getNodeId(), nodeElapsed, e.getMessage());
            completeNodeWithError(node, futures, nodeResultsMap, e.getMessage(), runId, emitter);
        }
    }

    // -------------------------------------------------------------------------
    // Single-node debug execution
    // -------------------------------------------------------------------------

    private void doExecuteNode(NodeDebugRequestDTO request, String runId, SseEmitter emitter) {
        WorkflowNodeDTO node = request.getNode();
        NodeExecuteContextDTO context = nodeExecuteDispatcher.buildContext(request);

        sendEvent(emitter, new NodeStartEvent(runId, node.getNodeId(),
                node.getNodeType(), System.currentTimeMillis()));

        try {
            NodeResultDTO result = nodeExecuteDispatcher.dispatch(node, context);
            StandardResultDTO streamResult = chunkAndStreamDataset(
                    result, node.getNodeId(), runId, DEFAULT_CHUNK_SIZE, emitter);

            sendEvent(emitter, new NodeResultEvent(
                    runId,
                    node.getNodeId(),
                    result.getStatus() != null ? result.getStatus().name() : "SUCCEEDED",
                    streamResult,
                    result.getMeta()));

            sendEvent(emitter, new WorkflowDoneEvent(runId, request.getWorkflowId(),
                    result.getStatus() != null ? result.getStatus().name() : "SUCCEEDED", 0));
        } catch (Exception e) {
            sendEvent(emitter, new WorkflowErrorEvent(runId, request.getWorkflowId(),
                    ErrorInfoDTO.builder()
                            .code(ErrorCode.INTERNAL_ERROR.getCode())
                            .message(e.getMessage())
                            .retryable(false)
                            .build()));
        } finally {
            emitter.complete();
        }
    }

    // -------------------------------------------------------------------------
    // Conditional routing
    // -------------------------------------------------------------------------

    private void activateConditionalEdges(String conditionNodeId,
                                           NodeResultDTO result,
                                           Map<String, List<ConditionalEdge>> conditionalEdgesMap,
                                           ConcurrentHashMap<String, CompletableFuture<NodeResultDTO>> futures,
                                           ConcurrentHashMap<String, NodeResultDTO> nodeResultsMap,
                                           String runId,
                                           SseEmitter emitter) {
        List<ConditionalEdge> edges = conditionalEdgesMap.get(conditionNodeId);
        if (edges == null || edges.isEmpty()) return;

        String branch = extractBranch(result);
        if (branch == null) return;

        for (ConditionalEdge edge : edges) {
            if (edge.condition() != null && !edge.condition().equals(branch)) {
                NodeResultDTO skipped = NodeResultDTO.builder()
                        .nodeId(edge.target())
                        .status(ExecutionStatus.SKIPPED)
                        .error(ErrorInfoDTO.builder()
                                .code("SKIPPED")
                                .message("condition node [" + conditionNodeId + "] branch=" + branch
                                        + ", edge.condition=" + edge.condition() + " → SKIPPED")
                                .retryable(false)
                                .build())
                        .build();
                nodeResultsMap.put(edge.target(), skipped);
                CompletableFuture<NodeResultDTO> future = futures.get(edge.target());
                if (future != null) {
                    future.complete(skipped);
                }
                sendEvent(emitter, new NodeResultEvent(runId, edge.target(), "SKIPPED", null, null));
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

    // -------------------------------------------------------------------------
    // Chunking helpers
    // -------------------------------------------------------------------------

    /**
     * 如果节点结果包含超过 LARGE_DATASET_THRESHOLD 行的数据集，则分块推送 node_progress 事件，
     * 并返回一个 rows=null 的精简 StandardResultDTO（rows 已通过 SSE 推送，避免重复传输）。
     * 否则直接返回原始结果（rows 将包含在 node_result 中）。
     */
    private StandardResultDTO chunkAndStreamDataset(NodeResultDTO result,
                                                     String nodeId,
                                                     String runId,
                                                     int chunkSize,
                                                     SseEmitter emitter) {
        if (result.getResult() == null) {
            return null;
        }
        StandardResultDTO stdResult = result.getResult();
        if (stdResult.getKind() != ResultKind.DATASET || stdResult.getDataset() == null) {
            return stdResult;
        }

        DatasetDTO dataset = stdResult.getDataset();
        List<Map<String, Object>> rows = dataset.getRows();
        if (rows == null || rows.size() <= LARGE_DATASET_THRESHOLD) {
            return stdResult;
        }

        // Chunk rows into node_progress events
        int totalRows = rows.size();
        int totalChunks = (int) Math.ceil((double) totalRows / chunkSize);
        for (int i = 0; i < totalChunks; i++) {
            int from = i * chunkSize;
            int to = Math.min(from + chunkSize, totalRows);
            List<Map<String, Object>> chunk = new ArrayList<>(rows.subList(from, to));
            sendEvent(emitter, new NodeProgressEvent(
                    runId, nodeId, i, i == 0 ? totalChunks : null, chunk));
        }

        // Return a stripped result (rows already pushed via node_progress)
        DatasetDTO strippedDataset = DatasetDTO.builder()
                .schema(dataset.getSchema())
                .rows(null)   // rows were pushed via node_progress
                .page(dataset.getPage())
                .stat(dataset.getStat())
                .build();
        return StandardResultDTO.builder()
                .kind(stdResult.getKind())
                .dataset(strippedDataset)
                .table(stdResult.getTable())
                .chart(stdResult.getChart())
                .variables(stdResult.getVariables())
                .build();
    }

    // -------------------------------------------------------------------------
    // Error helpers
    // -------------------------------------------------------------------------

    private void completeNodeWithSkip(WorkflowNodeDTO node,
                                      ConcurrentHashMap<String, CompletableFuture<NodeResultDTO>> futures,
                                      ConcurrentHashMap<String, NodeResultDTO> nodeResultsMap,
                                      String reason,
                                      String runId,
                                      SseEmitter emitter) {
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
        sendEvent(emitter, new NodeResultEvent(
                runId, node.getNodeId(), "SKIPPED", null, null));
    }

    private void completeNodeWithError(WorkflowNodeDTO node,
                                       ConcurrentHashMap<String, CompletableFuture<NodeResultDTO>> futures,
                                       ConcurrentHashMap<String, NodeResultDTO> nodeResultsMap,
                                       String message,
                                       String runId,
                                       SseEmitter emitter) {
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

        sendEvent(emitter, new NodeResultEvent(
                runId, node.getNodeId(), "FAILED", null, null));
    }

    // -------------------------------------------------------------------------
    // Run log helpers
    // -------------------------------------------------------------------------

    private void completeRunLog(String runId, String workflowId, String status,
                                 long elapsedMs, String nodeTraceJson) {
        try {
            workflowRunLogRepository.complete(runId, status, elapsedMs,
                    System.currentTimeMillis(), nodeTraceJson);
        } catch (Exception ex) {
            log.warn("Failed to complete stream run log runId={}: {}", runId, ex.getMessage());
        }
    }

    private String buildNodeTraceJson(List<WorkflowNodeDTO> nodes,
                                       ConcurrentHashMap<String, NodeResultDTO> nodeResultsMap) {
        List<Map<String, Object>> traces = new ArrayList<>();
        for (WorkflowNodeDTO node : nodes) {
            NodeResultDTO r = nodeResultsMap.get(node.getNodeId());
            if (r == null) continue;
            Map<String, Object> trace = new LinkedHashMap<>();
            trace.put("nodeId",   node.getNodeId());
            trace.put("nodeType", node.getNodeType());
            trace.put("status",   r.getStatus() != null ? r.getStatus().name() : "UNKNOWN");
            if (r.getError() != null) {
                trace.put("error", r.getError().getMessage());
            }
            traces.add(trace);
        }
        try {
            return objectMapper.writeValueAsString(traces);
        } catch (Exception e) {
            log.warn("Failed to serialize stream node traces: {}", e.getMessage());
            return "[]";
        }
    }

    // -------------------------------------------------------------------------
    // SSE send
    // -------------------------------------------------------------------------

    private void sendEvent(SseEmitter emitter, WorkflowStreamEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.eventType())
                    .data(objectMapper.writeValueAsString(event)));
        } catch (Exception e) {
            // Client may have disconnected; complete with error to stop further sends
            try {
                emitter.completeWithError(e);
            } catch (Exception ignored) {
                // already completed
            }
        }
    }
}
