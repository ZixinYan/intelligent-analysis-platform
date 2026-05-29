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
import com.kuaishou.intelligentanalysisplatform.contract.schema.ErrorHandlerNodeConfigDTO;
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
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class WorkflowStreamExecutor {

    private static final Logger log = LoggerFactory.getLogger(WorkflowStreamExecutor.class);
    static final int LARGE_DATASET_THRESHOLD = 1000;
    static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int WORKFLOW_TIMEOUT_MINUTES = 10;
    private static final String CONDITION_NODE_TYPE = "condition";
    private static final String ERROR_HANDLER_NODE_TYPE = "error_handler";

    record ConditionalEdge(String source, String target, String condition) {}

    private final NodeExecuteDispatcher nodeExecuteDispatcher;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public WorkflowStreamExecutor(NodeExecuteDispatcher nodeExecuteDispatcher,
                                  ObjectMapper objectMapper,
                                  MeterRegistry meterRegistry) {
        this.nodeExecuteDispatcher = nodeExecuteDispatcher;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    public void executeWorkflow(WorkflowRunRequestDTO request, SseEmitter emitter) {
        String runId = UUID.randomUUID().toString();
        ForkJoinPool.commonPool().execute(() -> doExecuteWorkflow(request, runId, emitter));
    }

    public void executeNode(NodeDebugRequestDTO request, SseEmitter emitter) {
        String runId = UUID.randomUUID().toString();
        ForkJoinPool.commonPool().execute(() -> doExecuteNode(request, runId, emitter));
    }

    private void doExecuteWorkflow(WorkflowRunRequestDTO request, String runId, SseEmitter emitter) {
        long workflowStart = System.currentTimeMillis();
        List<WorkflowNodeDTO> nodes = request.getNodes() != null ? request.getNodes() : List.of();
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
            Map<String, List<ConditionalEdge>> finalConditionalEdgesMap = conditionalEdgesMap;
            trigger.thenRunAsync(
                    () -> executeStreamNode(finalNode, deps, futures, completedResults,
                            nodeResultsMap, request, runId, emitter, finalConditionalEdgesMap),
                    ForkJoinPool.commonPool()
            ).exceptionally(ex -> {
                completeNodeWithError(finalNode, futures, nodeResultsMap, ex.getMessage(), runId, emitter);
                return null;
            });
        }

        try {
            CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]))
                    .get(WORKFLOW_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            futures.values().forEach(future -> future.cancel(true));
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
        }

        boolean anyFailed = nodeResultsMap.values().stream()
                .anyMatch(result -> result.getStatus() == ExecutionStatus.FAILED);
        String status = anyFailed ? "FAILED" : "SUCCEEDED";
        long elapsed = System.currentTimeMillis() - workflowStart;
        meterRegistry.counter("workflow.run.count",
                "status", status,
                "workflowId", request.getWorkflowId()).increment();
        meterRegistry.timer("workflow.run.duration",
                "workflowId", request.getWorkflowId())
                .record(elapsed, TimeUnit.MILLISECONDS);
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
                completeNodeWithSkip(node, futures, nodeResultsMap,
                        "upstream node [" + depId + "] threw exception", runId, emitter);
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
                completeNodeWithSkip(node, futures, nodeResultsMap,
                        "upstream node [" + depId + "] is " + depStatus + ", skipping", runId, emitter);
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
                .allNodes(request.getNodes())
                .build();

        sendEvent(emitter, new NodeStartEvent(runId, node.getNodeId(),
                node.getNodeType(), System.currentTimeMillis()));

        try {
            NodeResultDTO result = nodeExecuteDispatcher.dispatch(node, context);
            if (CONDITION_NODE_TYPE.equals(node.getNodeType())) {
                activateConditionalEdges(node.getNodeId(), result, conditionalEdgesMap,
                        futures, nodeResultsMap, runId, emitter);
            }

            StandardResultDTO streamResult = chunkAndStreamDataset(
                    result, node.getNodeId(), runId, DEFAULT_CHUNK_SIZE, emitter);
            nodeResultsMap.put(node.getNodeId(), result);
            if (result.getResult() != null) {
                completedResults.put(node.getNodeId(), result.getResult());
            }
            futures.get(node.getNodeId()).complete(result);
            sendEvent(emitter, new NodeResultEvent(
                    runId,
                    node.getNodeId(),
                    result.getStatus() != null ? result.getStatus().name() : "SUCCEEDED",
                    streamResult,
                    result.getMeta()));
        } catch (Exception e) {
            completeNodeWithError(node, futures, nodeResultsMap, e.getMessage(), runId, emitter);
        }
    }

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

    private void activateConditionalEdges(String conditionNodeId,
                                          NodeResultDTO result,
                                          Map<String, List<ConditionalEdge>> conditionalEdgesMap,
                                          ConcurrentHashMap<String, CompletableFuture<NodeResultDTO>> futures,
                                          ConcurrentHashMap<String, NodeResultDTO> nodeResultsMap,
                                          String runId,
                                          SseEmitter emitter) {
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

        int totalRows = rows.size();
        int totalChunks = (int) Math.ceil((double) totalRows / chunkSize);
        for (int i = 0; i < totalChunks; i++) {
            int from = i * chunkSize;
            int to = Math.min(from + chunkSize, totalRows);
            List<Map<String, Object>> chunk = new ArrayList<>(rows.subList(from, to));
            sendEvent(emitter, new NodeProgressEvent(
                    runId, nodeId, i, i == 0 ? totalChunks : null, chunk));
        }

        DatasetDTO strippedDataset = DatasetDTO.builder()
                .schema(dataset.getSchema())
                .rows(null)
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

    private void sendEvent(SseEmitter emitter, WorkflowStreamEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.eventType())
                    .data(objectMapper.writeValueAsString(event)));
        } catch (Exception e) {
            try {
                emitter.completeWithError(e);
            } catch (Exception ignored) {
            }
        }
    }
}
