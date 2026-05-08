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

import com.kuaishou.intelligentanalysisplatform.application.node.NodeExecuteDispatcher;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorInfoDTO;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowEdgeDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowNodeDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowRunRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowRunResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import org.springframework.stereotype.Component;

/**
 * DAG-based parallel workflow executor.
 * Nodes whose upstream dependencies are all satisfied are executed concurrently
 * via ForkJoinPool. Results are accumulated in a ConcurrentHashMap and snapshots
 * are passed to each node's execution context.
 */
@Component
public class WorkflowDagExecutor {

    private static final int WORKFLOW_TIMEOUT_MINUTES = 10;

    private final NodeExecuteDispatcher nodeExecuteDispatcher;

    public WorkflowDagExecutor(NodeExecuteDispatcher nodeExecuteDispatcher) {
        this.nodeExecuteDispatcher = nodeExecuteDispatcher;
    }

    public WorkflowRunResultDTO execute(WorkflowRunRequestDTO request, String runId) {
        List<WorkflowNodeDTO> nodes = request.getNodes();
        List<WorkflowEdgeDTO> edges = request.getEdges() != null ? request.getEdges() : List.of();

        // nodeId → node
        Map<String, WorkflowNodeDTO> nodeMap = new LinkedHashMap<>();
        // nodeId → set of direct upstream nodeIds
        Map<String, Set<String>> dependsOn = new HashMap<>();

        for (WorkflowNodeDTO node : nodes) {
            nodeMap.put(node.getNodeId(), node);
            dependsOn.put(node.getNodeId(), new HashSet<>());
        }

        for (WorkflowEdgeDTO edge : edges) {
            String src = edge.getSource();
            String tgt = edge.getTarget();
            if (src != null && tgt != null && nodeMap.containsKey(tgt)) {
                dependsOn.get(tgt).add(src);
            }
        }

        // Thread-safe state shared across all node executions
        ConcurrentHashMap<String, StandardResultDTO> completedResults = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, NodeResultDTO> nodeResultsMap = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, CompletableFuture<NodeResultDTO>> futures = new ConcurrentHashMap<>();

        for (WorkflowNodeDTO node : nodes) {
            futures.put(node.getNodeId(), new CompletableFuture<>());
        }

        // Wire up each node's execution to complete only after its deps are done
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
                    () -> executeNode(finalNode, deps, futures, completedResults, nodeResultsMap, request, runId),
                    ForkJoinPool.commonPool()
            ).exceptionally(ex -> {
                completeWithError(finalNode, futures, nodeResultsMap, ex.getMessage());
                return null;
            });
        }

        // Wait for all nodes to finish
        try {
            CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]))
                    .get(WORKFLOW_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            futures.values().forEach(f -> f.cancel(true));
            throw new BaseBusinessException(ErrorCode.INTERNAL_ERROR, "workflow execution timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            // individual node errors are captured inline, should not reach here
        }

        return buildResult(request.getWorkflowId(), nodes, nodeResultsMap);
    }

    private void executeNode(WorkflowNodeDTO node,
                             Set<String> deps,
                             ConcurrentHashMap<String, CompletableFuture<NodeResultDTO>> futures,
                             ConcurrentHashMap<String, StandardResultDTO> completedResults,
                             ConcurrentHashMap<String, NodeResultDTO> nodeResultsMap,
                             WorkflowRunRequestDTO request,
                             String runId) {
        // If any direct upstream failed, skip this node
        for (String depId : deps) {
            CompletableFuture<NodeResultDTO> depFuture = futures.get(depId);
            if (depFuture != null && depFuture.isDone()) {
                try {
                    NodeResultDTO depResult = depFuture.get();
                    if (depResult != null && depResult.getStatus() == ExecutionStatus.FAILED) {
                        completeWithError(node, futures, nodeResultsMap,
                                "upstream node [" + depId + "] failed, skipping");
                        return;
                    }
                } catch (Exception ignored) {
                    // treat as failed
                    completeWithError(node, futures, nodeResultsMap,
                            "upstream node [" + depId + "] threw exception");
                    return;
                }
            }
        }

        // Snapshot all currently completed results for deterministic execution
        Map<String, StandardResultDTO> snapshot = new LinkedHashMap<>(completedResults);

        NodeExecuteContextDTO context = NodeExecuteContextDTO.builder()
                .workflowId(request.getWorkflowId())
                .runId(runId)
                .nodeId(node.getNodeId())
                .upstreamResults(snapshot)
                .requestContext(request.getContext())
                .build();

        try {
            NodeResultDTO result = nodeExecuteDispatcher.dispatch(node, context);
            nodeResultsMap.put(node.getNodeId(), result);
            if (result.getResult() != null) {
                // Write result BEFORE completing the future so downstream snapshots see it
                completedResults.put(node.getNodeId(), result.getResult());
            }
            futures.get(node.getNodeId()).complete(result);
        } catch (Exception e) {
            completeWithError(node, futures, nodeResultsMap, e.getMessage());
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
            NodeResultDTO r = nodeResultsMap.get(node.getNodeId());
            if (r == null) {
                continue;
            }
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
}
