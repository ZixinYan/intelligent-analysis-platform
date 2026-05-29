package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import com.kuaishou.intelligentanalysisplatform.application.NodeMetadataApplicationService;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ResultKind;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetStatDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.IterationNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeRunMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowEdgeDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowNodeDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecutor;
import com.kuaishou.intelligentanalysisplatform.contract.spi.ValidationResultDTO;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 迭代节点执行器（非流式路径）。
 *
 * <p>从上游结果中解析输入数组，对每个元素顺序执行内部子图，
 * 并按 outputMode 聚合结果：
 * <ul>
 *   <li>FLATTEN（默认）：合并所有迭代的 Dataset 行为一个大 Dataset</li>
 *   <li>COLLECT：将每轮迭代的最终结果列表存入 variables._results</li>
 * </ul>
 *
 * <p>注意：流式执行路径由 {@link com.kuaishou.intelligentanalysisplatform.application.impl.WorkflowStreamExecutor}
 * 专门处理，会额外推送 iteration_started / iteration_next / iteration_finished SSE 事件。
 * 本 executor 仅供非流式（同步）路径使用。
 *
 * <p>{@link NodeExecuteDispatcher} 通过 {@code @Lazy} 注入以打破循环依赖：
 * IterationNodeExecutor → NodeExecuteDispatcher → NodeExecutorRegistry → IterationNodeExecutor。
 */
@Component
public class IterationNodeExecutor implements NodeExecutor<IterationNodeConfigDTO> {

    static final String OUTPUT_MODE_COLLECT = "COLLECT";
    static final int DEFAULT_MAX_ITERATIONS = 100;

    private final NodeMetadataApplicationService nodeMetadataApplicationService;
    private final RuntimeBindingResolver bindingResolver;
    /** @Lazy 打破循环依赖 */
    private final NodeExecuteDispatcher nodeExecuteDispatcher;

    public IterationNodeExecutor(NodeMetadataApplicationService nodeMetadataApplicationService,
                                 RuntimeBindingResolver bindingResolver,
                                 @Lazy NodeExecuteDispatcher nodeExecuteDispatcher) {
        this.nodeMetadataApplicationService = nodeMetadataApplicationService;
        this.bindingResolver = bindingResolver;
        this.nodeExecuteDispatcher = nodeExecuteDispatcher;
    }

    @Override
    public String supportType() {
        return "iteration";
    }

    @Override
    public NodeMetaDTO metadata() {
        return nodeMetadataApplicationService.getNodeDefinition(supportType());
    }

    @Override
    public ValidationResultDTO validate(IterationNodeConfigDTO config) {
        if (config == null || config.getInputArrayRef() == null
                || config.getInputArrayRef().getSourceNodeId() == null
                || config.getInputArrayRef().getSourceNodeId().isBlank()) {
            return ValidationResultDTO.builder()
                    .valid(false)
                    .errorMessage("iteration 节点必须配置 inputArrayRef.sourceNodeId")
                    .build();
        }
        if (config.getInnerNodes() == null || config.getInnerNodes().isEmpty()) {
            return ValidationResultDTO.builder()
                    .valid(false)
                    .errorMessage("iteration 节点的内部子图（innerNodes）不能为空")
                    .build();
        }
        return ValidationResultDTO.builder().valid(true).build();
    }

    @Override
    public NodeResultDTO execute(NodeExecuteContextDTO outerContext, IterationNodeConfigDTO config) {
        long start = System.currentTimeMillis();

        // 1. 解析输入数组
        Object rawValue = bindingResolver.resolveVariable(
                config.getInputArrayRef(), outerContext.getUpstreamResults());
        List<?> items = toList(rawValue);

        int maxIter = config.getMaxIterations() != null ? config.getMaxIterations() : DEFAULT_MAX_ITERATIONS;
        int totalCount = Math.min(items.size(), maxIter);

        // 2. 对每个元素执行内部子图
        List<DatasetDTO> collectedDatasets = new ArrayList<>();
        List<Object> collectedResults = new ArrayList<>();

        List<WorkflowNodeDTO> sortedInner = topologicalSort(
                config.getInnerNodes(), config.getInnerEdges());

        for (int i = 0; i < totalCount; i++) {
            Object item = items.get(i);
            Map<String, StandardResultDTO> innerUpstream =
                    buildInnerUpstream(outerContext.getUpstreamResults(), item);

            StandardResultDTO iterResult = executeInnerGraph(
                    sortedInner, outerContext, innerUpstream);

            if (iterResult != null) {
                if (iterResult.getKind() == ResultKind.DATASET && iterResult.getDataset() != null) {
                    collectedDatasets.add(iterResult.getDataset());
                }
                collectedResults.add(iterResult.getVariables());
            }
        }

        // 3. 聚合结果
        StandardResultDTO aggregated = aggregateResults(config.getOutputMode(),
                collectedDatasets, collectedResults);

        return NodeResultDTO.builder()
                .nodeId(outerContext.getNodeId())
                .nodeType(supportType())
                .status(ExecutionStatus.SUCCEEDED)
                .result(aggregated)
                .meta(NodeRunMetaDTO.builder()
                        .elapsedMs(System.currentTimeMillis() - start)
                        .build())
                .build();
    }

    // ── 内部子图执行 ──────────────────────────────────────────────────────────

    private StandardResultDTO executeInnerGraph(List<WorkflowNodeDTO> sortedInnerNodes,
                                                NodeExecuteContextDTO outerContext,
                                                Map<String, StandardResultDTO> initialUpstream) {
        Map<String, StandardResultDTO> results = new LinkedHashMap<>(initialUpstream);
        StandardResultDTO lastResult = null;

        for (WorkflowNodeDTO innerNode : sortedInnerNodes) {
            NodeExecuteContextDTO innerCtx = NodeExecuteContextDTO.builder()
                    .workflowId(outerContext.getWorkflowId())
                    .runId(outerContext.getRunId())
                    .nodeId(innerNode.getNodeId())
                    .upstreamResults(new LinkedHashMap<>(results))
                    .requestContext(outerContext.getRequestContext())
                    .allNodes(sortedInnerNodes)
                    .build();

            NodeResultDTO result = nodeExecuteDispatcher.dispatch(innerNode, innerCtx);
            if (result.getResult() != null) {
                results.put(innerNode.getNodeId(), result.getResult());
                lastResult = result.getResult();
            }
        }
        return lastResult;
    }

    // ── 拓扑排序（Kahn 算法）─────────────────────────────────────────────────

    /**
     * 对内部子图节点做拓扑排序，确保每个节点在其所有前驱执行完毕后再执行。
     * 若存在环则抛出异常。
     */
    List<WorkflowNodeDTO> topologicalSort(List<WorkflowNodeDTO> nodes,
                                          List<WorkflowEdgeDTO> edges) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }

        Map<String, WorkflowNodeDTO> nodeMap = new LinkedHashMap<>();
        for (WorkflowNodeDTO n : nodes) {
            nodeMap.put(n.getNodeId(), n);
        }

        // 构建入度表和邻接表
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adj = new HashMap<>();
        for (String id : nodeMap.keySet()) {
            inDegree.put(id, 0);
            adj.put(id, new ArrayList<>());
        }
        if (edges != null) {
            for (WorkflowEdgeDTO edge : edges) {
                String src = edge.getSource();
                String tgt = edge.getTarget();
                if (src == null || tgt == null
                        || !nodeMap.containsKey(src) || !nodeMap.containsKey(tgt)) {
                    continue;
                }
                adj.get(src).add(tgt);
                inDegree.merge(tgt, 1, Integer::sum);
            }
        }

        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<WorkflowNodeDTO> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String curr = queue.poll();
            sorted.add(nodeMap.get(curr));
            for (String next : adj.get(curr)) {
                int deg = inDegree.merge(next, -1, Integer::sum);
                if (deg == 0) {
                    queue.add(next);
                }
            }
        }

        if (sorted.size() != nodeMap.size()) {
            throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT,
                    "iteration 内部子图存在循环依赖，无法执行");
        }
        return sorted;
    }

    // ── 辅助方法 ─────────────────────────────────────────────────────────────

    private Map<String, StandardResultDTO> buildInnerUpstream(
            Map<String, StandardResultDTO> outerUpstream, Object item) {
        Map<String, StandardResultDTO> inner = new LinkedHashMap<>(outerUpstream);
        inner.put("$item", StandardResultDTO.builder()
                .kind(ResultKind.VARIABLES)
                .variables(Map.of("value", item))
                .build());
        return inner;
    }

    @SuppressWarnings("unchecked")
    private List<?> toList(Object rawValue) {
        if (rawValue == null) return List.of();
        if (rawValue instanceof List<?> list) return list;
        if (rawValue instanceof Collection<?> col) return new ArrayList<>(col);
        return List.of(rawValue);
    }

    private StandardResultDTO aggregateResults(String outputMode,
                                               List<DatasetDTO> datasets,
                                               List<Object> variablesList) {
        if (OUTPUT_MODE_COLLECT.equals(outputMode)) {
            return StandardResultDTO.builder()
                    .kind(ResultKind.VARIABLES)
                    .variables(Map.of("_results", variablesList))
                    .build();
        }
        // 默认 FLATTEN：合并所有 Dataset 的行
        return flattenDatasets(datasets);
    }

    private StandardResultDTO flattenDatasets(List<DatasetDTO> datasets) {
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
}
