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

    private static final int WORKFLOW_TIMEOUT_MINUTES = 10;

    private final NodeExecuteDispatcher nodeExecuteDispatcher;
    private final ObjectMapper objectMapper;

    public WorkflowStreamExecutor(NodeExecuteDispatcher nodeExecuteDispatcher, ObjectMapper objectMapper) {
        this.nodeExecuteDispatcher = nodeExecuteDispatcher;
        this.objectMapper = objectMapper;
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

        // Build dependency graph (same logic as WorkflowDagExecutor)
        Map<String, WorkflowNodeDTO> nodeMap = new LinkedHashMap<>();
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
            trigger.thenRunAsync(
                    () -> executeStreamNode(finalNode, deps, futures, completedResults,
                            nodeResultsMap, request, runId, emitter),
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
                                   SseEmitter emitter) {
        // Skip if any upstream failed
        for (String depId : deps) {
            CompletableFuture<NodeResultDTO> depFuture = futures.get(depId);
            if (depFuture != null && depFuture.isDone()) {
                try {
                    NodeResultDTO depResult = depFuture.get();
                    if (depResult != null && depResult.getStatus() == ExecutionStatus.FAILED) {
                        completeNodeWithError(node, futures, nodeResultsMap,
                                "upstream node [" + depId + "] failed, skipping", runId, emitter);
                        return;
                    }
                } catch (Exception ignored) {
                    completeNodeWithError(node, futures, nodeResultsMap,
                            "upstream node [" + depId + "] threw exception", runId, emitter);
                    return;
                }
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
                .build();

        // Push node_start
        sendEvent(emitter, new NodeStartEvent(runId, node.getNodeId(),
                node.getNodeType(), System.currentTimeMillis()));

        long nodeStart = System.currentTimeMillis();
        try {
            NodeResultDTO result = nodeExecuteDispatcher.dispatch(node, context);

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
