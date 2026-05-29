package com.kuaishou.intelligentanalysisplatform.application.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
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
import com.kuaishou.intelligentanalysisplatform.application.node.RuntimeBindingResolver;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorInfoDTO;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ResultKind;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetStatDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ErrorHandlerNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.IterationNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeDebugRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeRunMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowEdgeDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowNodeDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowRunRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.stream.WorkflowStreamEvent;
import com.kuaishou.intelligentanalysisplatform.contract.schema.stream.WorkflowStreamEvent.IterationFinishedEvent;
import com.kuaishou.intelligentanalysisplatform.contract.schema.stream.WorkflowStreamEvent.IterationNextEvent;
import com.kuaishou.intelligentanalysisplatform.contract.schema.stream.WorkflowStreamEvent.IterationStartedEvent;
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
    private static final String ITERATION_NODE_TYPE = "iteration";
    private static final int DEFAULT_MAX_ITERATIONS = 100;

    record ConditionalEdge(String source, String target, String condition) {}

    private final NodeExecuteDispatcher nodeExecuteDispatcher;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final RuntimeBindingResolver bindingResolver;

    public WorkflowStreamExecutor(NodeExecuteDispatcher nodeExecuteDispatcher,
                                  ObjectMapper objectMapper,
                                  MeterRegistry meterRegistry,
                                  RuntimeBindingResolver bindingResolver) {
        this.nodeExecuteDispatcher = nodeExecuteDispatcher;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.bindingResolver = bindingResolver;
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

        if (ITERATION_NODE_TYPE.equals(node.getNodeType())) {
            executeIterationStreamNode(node, context, futures, completedResults,
                    nodeResultsMap, runId, emitter);
            return;
        }

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
                    result.getMeta(),
                    result.getError()));
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
                    result.getMeta(),
                    result.getError()));
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
                sendEvent(emitter, new NodeResultEvent(runId, edge.target(), "SKIPPED", null, null,
                        skipped.getError()));
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
                runId, node.getNodeId(), "SKIPPED", null, null, skipped.getError()));
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
                runId, node.getNodeId(), "FAILED", null, null, failed.getError()));
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

    // ── 迭代节点流式执行 ─────────────────────────────────────────────────────

    /**
     * 迭代节点的流式执行入口：对输入数组的每个元素顺序执行内部子图，
     * 并实时推送 iteration_started / iteration_next / iteration_finished 事件。
     */
    private void executeIterationStreamNode(WorkflowNodeDTO node,
                                            NodeExecuteContextDTO outerContext,
                                            ConcurrentHashMap<String, CompletableFuture<NodeResultDTO>> futures,
                                            ConcurrentHashMap<String, StandardResultDTO> completedResults,
                                            ConcurrentHashMap<String, NodeResultDTO> nodeResultsMap,
                                            String runId,
                                            SseEmitter emitter) {
        long start = System.currentTimeMillis();

        IterationNodeConfigDTO config;
        try {
            config = objectMapper.convertValue(node.getConfig(), IterationNodeConfigDTO.class);
        } catch (Exception e) {
            completeNodeWithError(node, futures, nodeResultsMap,
                    "iteration 节点配置解析失败: " + e.getMessage(), runId, emitter);
            return;
        }

        if (config.getInputArrayRef() == null || config.getInputArrayRef().getSourceNodeId() == null) {
            completeNodeWithError(node, futures, nodeResultsMap,
                    "iteration 节点未配置 inputArrayRef", runId, emitter);
            return;
        }

        // 1. 解析输入数组
        Object rawValue = bindingResolver.resolveVariable(
                config.getInputArrayRef(), outerContext.getUpstreamResults());
        List<?> items = iterToList(rawValue);
        int maxIter = config.getMaxIterations() != null ? config.getMaxIterations() : DEFAULT_MAX_ITERATIONS;
        int totalCount = Math.min(items.size(), maxIter);

        // 2. iteration_started
        sendEvent(emitter, new IterationStartedEvent(runId, node.getNodeId(),
                node.getNodeType(), totalCount));

        // 3. 拓扑排序内部子图
        List<WorkflowNodeDTO> sortedInner;
        try {
            sortedInner = iterTopologicalSort(config.getInnerNodes(), config.getInnerEdges());
        } catch (Exception e) {
            completeNodeWithError(node, futures, nodeResultsMap,
                    "iteration 内部子图排序失败: " + e.getMessage(), runId, emitter);
            return;
        }

        // 4. 逐元素执行
        List<DatasetDTO> collectedDatasets = new ArrayList<>();
        List<Object> collectedVars = new ArrayList<>();

        for (int i = 0; i < totalCount; i++) {
            Object item = items.get(i);
            Map<String, StandardResultDTO> innerUpstream =
                    buildIterInnerUpstream(outerContext.getUpstreamResults(), item);

            StandardResultDTO iterResult = runIterInnerGraph(sortedInner, outerContext, innerUpstream);
            if (iterResult != null) {
                if (iterResult.getKind() == ResultKind.DATASET && iterResult.getDataset() != null) {
                    collectedDatasets.add(iterResult.getDataset());
                }
                collectedVars.add(iterResult.getVariables());
            }

            // iteration_next（0-based）
            sendEvent(emitter, new IterationNextEvent(runId, node.getNodeId(), i));
        }

        // 5. 聚合
        StandardResultDTO aggregated = aggregateIterResults(
                config.getOutputMode(), collectedDatasets, collectedVars);
        long elapsed = System.currentTimeMillis() - start;
        NodeRunMetaDTO meta = NodeRunMetaDTO.builder().elapsedMs(elapsed).build();

        NodeResultDTO nodeResult = NodeResultDTO.builder()
                .nodeId(node.getNodeId())
                .nodeType(node.getNodeType())
                .status(ExecutionStatus.SUCCEEDED)
                .result(aggregated)
                .meta(meta)
                .build();

        nodeResultsMap.put(node.getNodeId(), nodeResult);
        if (aggregated != null) {
            completedResults.put(node.getNodeId(), aggregated);
        }
        futures.get(node.getNodeId()).complete(nodeResult);

        // 6. iteration_finished + node_result
        sendEvent(emitter, new IterationFinishedEvent(runId, node.getNodeId(),
                "SUCCEEDED", aggregated, meta, null));
        sendEvent(emitter, new NodeResultEvent(runId, node.getNodeId(), "SUCCEEDED",
                aggregated, meta, null));
    }

    /** 在迭代内部顺序执行子图节点，返回最后一个有效结果 */
    private StandardResultDTO runIterInnerGraph(List<WorkflowNodeDTO> sortedNodes,
                                                NodeExecuteContextDTO outerCtx,
                                                Map<String, StandardResultDTO> initUpstream) {
        Map<String, StandardResultDTO> results = new LinkedHashMap<>(initUpstream);
        StandardResultDTO lastResult = null;

        for (WorkflowNodeDTO innerNode : sortedNodes) {
            NodeExecuteContextDTO ctx = NodeExecuteContextDTO.builder()
                    .workflowId(outerCtx.getWorkflowId())
                    .runId(outerCtx.getRunId())
                    .nodeId(innerNode.getNodeId())
                    .upstreamResults(new LinkedHashMap<>(results))
                    .requestContext(outerCtx.getRequestContext())
                    .allNodes(sortedNodes)
                    .build();

            NodeResultDTO result = nodeExecuteDispatcher.dispatch(innerNode, ctx);
            if (result.getResult() != null) {
                results.put(innerNode.getNodeId(), result.getResult());
                lastResult = result.getResult();
            }
        }
        return lastResult;
    }

    private Map<String, StandardResultDTO> buildIterInnerUpstream(
            Map<String, StandardResultDTO> outerUpstream, Object item) {
        Map<String, StandardResultDTO> inner = new LinkedHashMap<>(outerUpstream);
        inner.put("$item", StandardResultDTO.builder()
                .kind(ResultKind.VARIABLES)
                .variables(Map.of("value", item))
                .build());
        return inner;
    }

    private StandardResultDTO aggregateIterResults(String outputMode,
                                                   List<DatasetDTO> datasets,
                                                   List<Object> vars) {
        if ("COLLECT".equals(outputMode)) {
            return StandardResultDTO.builder()
                    .kind(ResultKind.VARIABLES)
                    .variables(Map.of("_results", vars))
                    .build();
        }
        // FLATTEN（默认）
        if (datasets.isEmpty()) {
            return StandardResultDTO.builder()
                    .kind(ResultKind.DATASET)
                    .dataset(DatasetDTO.builder()
                            .rows(List.of())
                            .stat(DatasetStatDTO.builder().rowCount(0).build())
                            .build())
                    .build();
        }
        List<Map<String, Object>> allRows = new ArrayList<>();
        DatasetSchemaDTO schema = null;
        for (DatasetDTO ds : datasets) {
            if (schema == null && ds.getSchema() != null) {
                schema = ds.getSchema();
            }
            if (ds.getRows() != null) {
                allRows.addAll(ds.getRows());
            }
        }
        return StandardResultDTO.builder()
                .kind(ResultKind.DATASET)
                .dataset(DatasetDTO.builder()
                        .schema(schema)
                        .rows(allRows)
                        .stat(DatasetStatDTO.builder().rowCount(allRows.size()).build())
                        .build())
                .build();
    }

    private List<?> iterToList(Object rawValue) {
        if (rawValue == null) return List.of();
        if (rawValue instanceof List<?> list) return list;
        if (rawValue instanceof Collection<?> col) return new ArrayList<>(col);
        return List.of(rawValue);
    }

    /** Kahn 拓扑排序：用于内部子图节点排序 */
    private List<WorkflowNodeDTO> iterTopologicalSort(List<WorkflowNodeDTO> nodes,
                                                      List<WorkflowEdgeDTO> edges) {
        if (nodes == null || nodes.isEmpty()) return List.of();

        Map<String, WorkflowNodeDTO> nodeMap = new LinkedHashMap<>();
        for (WorkflowNodeDTO n : nodes) nodeMap.put(n.getNodeId(), n);

        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adj = new HashMap<>();
        for (String id : nodeMap.keySet()) {
            inDegree.put(id, 0);
            adj.put(id, new ArrayList<>());
        }
        if (edges != null) {
            for (WorkflowEdgeDTO edge : edges) {
                String src = edge.getSource(), tgt = edge.getTarget();
                if (src == null || tgt == null
                        || !nodeMap.containsKey(src) || !nodeMap.containsKey(tgt)) continue;
                adj.get(src).add(tgt);
                inDegree.merge(tgt, 1, Integer::sum);
            }
        }

        Queue<String> queue = new LinkedList<>();
        inDegree.forEach((id, deg) -> { if (deg == 0) queue.add(id); });

        List<WorkflowNodeDTO> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String curr = queue.poll();
            sorted.add(nodeMap.get(curr));
            for (String next : adj.get(curr)) {
                if (inDegree.merge(next, -1, Integer::sum) == 0) queue.add(next);
            }
        }
        if (sorted.size() != nodeMap.size()) {
            throw new IllegalStateException("iteration 内部子图存在循环依赖");
        }
        return sorted;
    }
}
