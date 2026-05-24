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
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowRunRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowRunResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 工作流 DAG 并行执行引擎。
 *
 * <p>核心算法：
 * <ol>
 *   <li><b>依赖图构建</b>：解析边（edges）计算每个节点的前驱集合（dependsOn），
 *       支持普通边和带条件标签（true/false）的条件边。</li>
 *   <li><b>基于 CompletableFuture 的并行执行</b>：每个节点对应一个 Future，
 *       节点就绪（所有前驱完成）后，投入 ForkJoinPool 公共线程池异步执行。
 *       无前驱节点（起始节点）立即触发。</li>
 *   <li><b>条件分支剪枝</b>：ConditionNode 执行后从结果变量 {@code _branch}
 *       读取走向（"true"/"false"），将不满足条件的边的目标节点直接置为 SKIPPED。</li>
 *   <li><b>异常传播</b>：上游节点 FAILED / SKIPPED 时，下游节点联动 SKIPPED，
 *       ErrorHandler 节点除外（可捕获指定被守护节点的失败）。</li>
 *   <li><b>整体超时</b>：所有节点 10 分钟内未完成则强制取消并抛出异常。</li>
 * </ol>
 *
 * <p>执行完成后向 Micrometer 上报 workflow.run.count 计数器和 workflow.run.duration 耗时，
 * 标签包含 workflowId 和最终 status，便于接入 Prometheus + Grafana 监控。
 */
@Component
public class WorkflowDagExecutor {

    /** 工作流整体执行超时时间（分钟）。超过此时间所有未完成节点 Future 被取消 */
    private static final int WORKFLOW_TIMEOUT_MINUTES = 10;
    /** 条件节点的 nodeType 标识，用于识别需要进行分支剪枝的节点 */
    private static final String CONDITION_NODE_TYPE = "condition";
    /** 错误处理节点的 nodeType 标识，允许捕获指定上游节点的失败状态 */
    private static final String ERROR_HANDLER_NODE_TYPE = "error_handler";

    private final NodeExecuteDispatcher nodeExecuteDispatcher;
    private final MeterRegistry meterRegistry;
    private final Executor workflowIoExecutor;

    public WorkflowDagExecutor(NodeExecuteDispatcher nodeExecuteDispatcher,
                               ObjectMapper objectMapper,
                               MeterRegistry meterRegistry,
                               @Qualifier("workflowIoExecutor") Executor workflowIoExecutor) {
        this.nodeExecuteDispatcher = nodeExecuteDispatcher;
        this.meterRegistry = meterRegistry;
        this.workflowIoExecutor = workflowIoExecutor;
    }

    /** 有条件边的元数据：记录边的源节点、目标节点和条件标签（"true"/"false"）*/
    record ConditionalEdge(String source, String target, String condition) {}

    /**
     * 执行工作流 DAG。
     *
     * <p>此方法是阻塞的：在所有节点完成（或超时/中断）后才返回。
     * 节点之间的并行度由 ForkJoinPool 公共线程池决定，
     * 适合 CPU 密集度低、以 IO 等待为主的节点类型（SQL 查询、外部 API 调用等）。
     *
     * @param request 工作流运行请求，包含节点定义、边定义和输入参数
     * @param runId   本次运行的唯一 ID，用于 SSE 推送和日志追踪
     * @return 包含所有节点结果、最终数据集及工作流整体状态的运行结果
     * @throws BaseBusinessException 执行超时时抛出，错误码 INTERNAL_ERROR
     */
    public WorkflowRunResultDTO execute(WorkflowRunRequestDTO request, String runId) {
        List<WorkflowNodeDTO> nodes = request.getNodes();
        List<WorkflowEdgeDTO> edges = request.getEdges() != null ? request.getEdges() : List.of();

        Map<String, WorkflowNodeDTO> nodeMap = new LinkedHashMap<>();
        Map<String, Set<String>> dependsOn = new HashMap<>();
        for (WorkflowNodeDTO node : nodes) {
            nodeMap.put(node.getNodeId(), node);
            dependsOn.put(node.getNodeId(), new HashSet<>());
        }

        Map<String, List<ConditionalEdge>> conditionalEdgesMap = new HashMap<>();
        for (WorkflowEdgeDTO edge : edges) {
            String src = edge.getSource();
            String tgt = edge.getTarget();
            if (src == null || tgt == null || !nodeMap.containsKey(tgt)) {
                continue;
            }
            dependsOn.get(tgt).add(src);
            if (edge.getCondition() != null) {
                conditionalEdgesMap.computeIfAbsent(src, key -> new ArrayList<>())
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

            WorkflowNodeDTO finalNode = node;
            trigger.thenRunAsync(
                    () -> executeNode(finalNode, deps, futures, completedResults, nodeResultsMap,
                            request, runId, nodes, conditionalEdgesMap),
                    workflowIoExecutor
            ).exceptionally(ex -> {
                completeWithError(finalNode, futures, nodeResultsMap, ex.getMessage());
                return null;
            });
        }

        try {
            CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]))
                    .get(WORKFLOW_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            futures.values().forEach(future -> future.cancel(true));
            throw new BaseBusinessException(ErrorCode.INTERNAL_ERROR, "workflow execution timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
        }

        WorkflowRunResultDTO result = buildResult(request.getWorkflowId(), nodes, nodeResultsMap);
        String finalStatus = result.getStatus() != null ? result.getStatus().name() : "SUCCEEDED";
        long elapsedMs = nodeResultsMap.values().stream()
                .map(NodeResultDTO::getMeta)
                .filter(Objects::nonNull)
                .mapToLong(meta -> meta.getElapsedMs() == null ? 0L : meta.getElapsedMs())
                .sum();
        meterRegistry.counter("workflow.run.count",
                "status", finalStatus,
                "workflowId", request.getWorkflowId()).increment();
        meterRegistry.timer("workflow.run.duration",
                "workflowId", request.getWorkflowId())
                .record(elapsedMs, TimeUnit.MILLISECONDS);
        return result;
    }

    /**
     * 在异步线程中执行单个节点。
     *
     * <p>执行前先检查所有上游节点的结果：
     * <ul>
     *   <li>上游 FAILED / SKIPPED → 当前节点置为 SKIPPED（级联跳过）</li>
     *   <li>上游是被当前 ErrorHandler 守护的节点且状态为 FAILED → 允许继续执行</li>
     *   <li>所有上游正常 → 调用 {@link NodeExecuteDispatcher#dispatch} 实际执行</li>
     * </ul>
     *
     * <p>ConditionNode 执行成功后，额外触发 {@link #activateConditionalEdges} 进行分支剪枝。
     *
     * @param node              当前要执行的节点
     * @param deps              该节点的上游节点 ID 集合
     * @param futures           全局 Future 注册表
     * @param completedResults  已完成节点的结果快照（传递给下游作为上下文）
     * @param nodeResultsMap    节点执行结果注册表
     * @param request           原始工作流运行请求
     * @param runId             本次运行 ID
     * @param allNodes          全部节点列表（传给 NodeExecuteContext）
     * @param conditionalEdgesMap 条件边索引（key=sourceNodeId）
     */
    private void executeNode(WorkflowNodeDTO node,
                             Set<String> deps,
                             ConcurrentHashMap<String, CompletableFuture<NodeResultDTO>> futures,
                             ConcurrentHashMap<String, StandardResultDTO> completedResults,
                             ConcurrentHashMap<String, NodeResultDTO> nodeResultsMap,
                             WorkflowRunRequestDTO request,
                             String runId,
                             List<WorkflowNodeDTO> allNodes,
                             Map<String, List<ConditionalEdge>> conditionalEdgesMap) {
        if (futures.get(node.getNodeId()).isDone()) {
            return;
        }

        for (String depId : deps) {
            CompletableFuture<NodeResultDTO> depFuture = futures.get(depId);
            if (depFuture == null || !depFuture.isDone()) {
                continue;
            }

            NodeResultDTO depResult;
            try {
                depResult = depFuture.get();
            } catch (Exception ignored) {
                completeWithSkip(node, futures, nodeResultsMap,
                        "upstream [" + depId + "] threw exception, skipping");
                return;
            }
            if (depResult == null) {
                continue;
            }

            ExecutionStatus depStatus = depResult.getStatus();
            if (depStatus == ExecutionStatus.FAILED || depStatus == ExecutionStatus.SKIPPED) {
                if (ERROR_HANDLER_NODE_TYPE.equals(node.getNodeType())
                        && depStatus == ExecutionStatus.FAILED
                        && node.getConfig() instanceof ErrorHandlerNodeConfigDTO ehConfig
                        && depId.equals(ehConfig.getGuardedNodeId())) {
                    continue;
                }
                completeWithSkip(node, futures, nodeResultsMap,
                        "upstream [" + depId + "] is " + depStatus + ", skipping");
                return;
            }
        }

        Map<String, StandardResultDTO> snapshot = new LinkedHashMap<>(completedResults);
        NodeExecuteContextDTO context = NodeExecuteContextDTO.builder()
                .workflowId(request.getWorkflowId())
                .runId(runId)
                .nodeId(node.getNodeId())
                .upstreamResults(snapshot)
                .requestContext(request.getContext())
                .allNodes(allNodes)
                .build();

        try {
            NodeResultDTO result = nodeExecuteDispatcher.dispatch(node, context);
            nodeResultsMap.put(node.getNodeId(), result);
            if (result.getResult() != null) {
                completedResults.put(node.getNodeId(), result.getResult());
            }
            if (CONDITION_NODE_TYPE.equals(node.getNodeType())) {
                activateConditionalEdges(node.getNodeId(), result, conditionalEdgesMap, futures, nodeResultsMap);
            }
            futures.get(node.getNodeId()).complete(result);
        } catch (Exception e) {
            completeWithError(node, futures, nodeResultsMap, e.getMessage());
        }
    }

    /**
     * 根据条件节点的执行结果激活/剪枝条件边。
     *
     * <p>从节点结果的变量字典中读取 {@code _branch} 字段（值为 "true" 或 "false"），
     * 将条件标签不匹配的目标节点直接预置为 SKIPPED，使其不会再被 ForkJoinPool 触发执行。
     *
     * @param conditionNodeId     条件节点 ID
     * @param result              条件节点的执行结果
     * @param conditionalEdgesMap 条件边索引
     * @param futures             全局 Future 注册表
     * @param nodeResultsMap      节点结果注册表
     */
    private void activateConditionalEdges(String conditionNodeId,
                                          NodeResultDTO result,
                                          Map<String, List<ConditionalEdge>> conditionalEdgesMap,
                                          ConcurrentHashMap<String, CompletableFuture<NodeResultDTO>> futures,
                                          ConcurrentHashMap<String, NodeResultDTO> nodeResultsMap) {
        List<ConditionalEdge> edges = conditionalEdgesMap.get(conditionNodeId);
        if (edges == null || edges.isEmpty()) {
            return;
        }

        String branch = extractBranch(result);
        if (branch == null) {
            return;
        }

        for (ConditionalEdge edge : edges) {
            if (edge.condition() != null && !edge.condition().equals(branch)) {
                preCompleteWithSkip(edge.target(), futures, nodeResultsMap,
                        "condition node [" + conditionNodeId + "] branch=" + branch
                                + ", edge.condition=" + edge.condition() + " → SKIPPED");
            }
        }
    }

    /**
     * 从条件节点结果的变量字典中提取分支方向（"true" 或 "false"）。
     * 返回 null 表示结果不含 _branch，分支剪枝不生效。
     */
    private String extractBranch(NodeResultDTO result) {
        if (result == null || result.getResult() == null) {
            return null;
        }
        Map<String, Object> vars = result.getResult().getVariables();
        if (vars == null) {
            return null;
        }
        Object branch = vars.get("_branch");
        return branch != null ? branch.toString() : null;
    }

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

    /**
     * 按节点定义顺序整合所有节点结果，确定工作流整体状态。
     *
     * <p>只要有一个节点 FAILED，工作流状态就为 FAILED。
     * {@code finalResult} 取最后一个有非空结果的节点（通常是输出节点）。
     */
    private WorkflowRunResultDTO buildResult(String workflowId,
                                             List<WorkflowNodeDTO> nodes,
                                             ConcurrentHashMap<String, NodeResultDTO> nodeResultsMap) {
        List<NodeResultDTO> orderedResults = new ArrayList<>();
        StandardResultDTO finalResult = null;
        String finalResultNodeId = null;
        ExecutionStatus workflowStatus = ExecutionStatus.SUCCEEDED;

        for (WorkflowNodeDTO node : nodes) {
            NodeResultDTO result = nodeResultsMap.get(node.getNodeId());
            if (result == null) {
                continue;
            }
            orderedResults.add(result);
            if (result.getStatus() == ExecutionStatus.FAILED) {
                workflowStatus = ExecutionStatus.FAILED;
            }
            if (result.getResult() != null) {
                finalResult = result.getResult();
                finalResultNodeId = node.getNodeId();
            }
        }

        return WorkflowRunResultDTO.builder()
                .workflowId(workflowId)
                .status(workflowStatus)
                .nodeResults(orderedResults)
                .finalResult(finalResult)
                .finalResultNodeId(finalResultNodeId)
                .build();
    }
}
