package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.application.NodeMetadataApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeDatasetResolver;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeResultFactory;
import com.kuaishou.intelligentanalysisplatform.application.compute.InMemoryAggregateComputeService;
import com.kuaishou.intelligentanalysisplatform.contract.schema.AggregateMetricDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.AggregateNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ComputeAuditDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ComputeStepDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SortFieldDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecutor;
import com.kuaishou.intelligentanalysisplatform.contract.spi.ValidationResultDTO;
import org.springframework.stereotype.Component;

@Component
public class AggregateNodeExecutor implements NodeExecutor<AggregateNodeConfigDTO> {
    private final NodeMetadataApplicationService nodeMetadataApplicationService;
    private final ComputeDatasetResolver computeDatasetResolver;
    private final InMemoryAggregateComputeService aggregateComputeService;
    private final ComputeResultFactory computeResultFactory;

    public AggregateNodeExecutor(NodeMetadataApplicationService nodeMetadataApplicationService,
                                 ComputeDatasetResolver computeDatasetResolver,
                                 InMemoryAggregateComputeService aggregateComputeService,
                                 ComputeResultFactory computeResultFactory) {
        this.nodeMetadataApplicationService = nodeMetadataApplicationService;
        this.computeDatasetResolver = computeDatasetResolver;
        this.aggregateComputeService = aggregateComputeService;
        this.computeResultFactory = computeResultFactory;
    }

    @Override
    public String supportType() {
        return "aggregate";
    }

    @Override
    public NodeResultDTO execute(NodeExecuteContextDTO context, AggregateNodeConfigDTO config) {
        long start = System.currentTimeMillis();
        DatasetDTO input = computeDatasetResolver.resolve(config.getDatasetRef(), context.getUpstreamResults());
        DatasetDTO output = aggregateComputeService.compute(config, input);
        return computeResultFactory.success(
                context.getNodeId(),
                supportType(),
                output,
                Boolean.TRUE.equals(config.getPushdownEnabled()),
                supportType(),
                System.currentTimeMillis() - start,
                buildAudit(config, input, output)
        );
    }

    @Override
    public ValidationResultDTO validate(AggregateNodeConfigDTO config) {
        if (config == null || config.getMetrics() == null || config.getMetrics().isEmpty()) {
            return ValidationResultDTO.builder().valid(false).errorMessage("aggregate metrics are required").build();
        }
        for (AggregateMetricDTO metric : config.getMetrics()) {
            if (metric == null || metric.getField() == null || metric.getField().isBlank() || metric.getAgg() == null) {
                return ValidationResultDTO.builder().valid(false).errorMessage("aggregate metric field and agg are required").build();
            }
        }
        return ValidationResultDTO.builder().valid(true).build();
    }

    @Override
    public NodeMetaDTO metadata() {
        return nodeMetadataApplicationService.getNodeDefinition(supportType());
    }

    private ComputeAuditDTO buildAudit(AggregateNodeConfigDTO config, DatasetDTO input, DatasetDTO output) {
        List<ComputeStepDTO> steps = new ArrayList<>();
        steps.add(ComputeStepDTO.builder()
                .stepName("group_by")
                .description("按字段分组")
                .params(Map.of("fields", config.getGroupByFields() == null ? List.of() : config.getGroupByFields()))
                .build());
        steps.add(ComputeStepDTO.builder()
                .stepName("aggregate")
                .description("计算聚合指标")
                .params(Map.of("metrics", metricParams(config.getMetrics())))
                .build());
        if (config.getSortFields() != null && !config.getSortFields().isEmpty()) {
            steps.add(ComputeStepDTO.builder()
                    .stepName("sort")
                    .description("聚合结果排序")
                    .params(Map.of("sortFields", sortParams(config.getSortFields())))
                    .build());
        }
        if (config.getTopN() != null) {
            steps.add(ComputeStepDTO.builder()
                    .stepName("top_n")
                    .description("截取前N条聚合结果")
                    .params(Map.of("topN", config.getTopN()))
                    .build());
        }
        return ComputeAuditDTO.builder()
                .capabilityType(supportType())
                .steps(steps)
                .inputRowCount(rowCount(input))
                .outputRowCount(rowCount(output))
                .build();
    }

    private List<Map<String, Object>> metricParams(List<AggregateMetricDTO> metrics) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (metrics == null) {
            return result;
        }
        for (AggregateMetricDTO metric : metrics) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("field", metric.getField());
            item.put("agg", metric.getAgg() == null ? null : metric.getAgg().name());
            item.put("alias", metric.getAlias());
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> sortParams(List<SortFieldDTO> sortFields) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SortFieldDTO sortField : sortFields) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("field", sortField.getField());
            item.put("order", sortField.getOrder());
            result.add(item);
        }
        return result;
    }

    private Integer rowCount(DatasetDTO dataset) {
        if (dataset == null) {
            return 0;
        }
        if (dataset.getStat() != null && dataset.getStat().getRowCount() != null) {
            return dataset.getStat().getRowCount();
        }
        return dataset.getRows() == null ? 0 : dataset.getRows().size();
    }
}
