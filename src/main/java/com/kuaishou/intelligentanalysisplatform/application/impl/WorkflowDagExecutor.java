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
import org.springframework.stereotype.Component;

@Component
public class WorkflowDagExecutor {

    private static final int WORKFLOW_TIMEOUT_MINUTES = 10;
    private static final String CONDITION_NODE_TYPE = "condition";
    private static final String ERROR_HANDLER_NODE_TYPE = "error_handler";

    private final NodeExecuteDispatcher nodeExecuteDispatcher;
    private final MeterRegistry meterRegistry;

    public WorkflowDagExecutor(NodeExecuteDispatcher nodeExecuteDispatcher,
                               ObjectMapper objectMapper,
                               MeterRegistry meterRegistry) {
        this.nodeExecuteDispatcher = nodeExecuteDispatcher;
        this.meterRegistry = meterRegistry;
    }

    record ConditionalEdge(String source, String target, String condition) {}

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
                    ForkJoinPool.commonPool()
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

    private WorkflowRunResultDTO buildResult(String workflowId,
                                             List<WorkflowNodeDTO> nodes,
                                             ConcurrentHashMap<String, NodeResultDTO> nodeResultsMap) {
        List<NodeResultDTO> orderedResults = new ArrayList<>();
        StandardResultDTO finalResult = null;
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
            }
        }

        return WorkflowRunResultDTO.builder()
                .workflowId(workflowId)
                .status(workflowStatus)
                .nodeResults(orderedResults)
                .finalResult(finalResult)
                .build();
    }
}
