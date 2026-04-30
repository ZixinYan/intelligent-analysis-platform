package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.application.NodeMetadataApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeDatasetResolver;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeResultFactory;
import com.kuaishou.intelligentanalysisplatform.application.compute.InMemoryTimeSeriesComputeService;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ComputeAuditDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ComputeStepDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.MetricComputeRuleDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.TimeSeriesComputeNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecutor;
import com.kuaishou.intelligentanalysisplatform.contract.spi.ValidationResultDTO;
import org.springframework.stereotype.Component;

@Component
public class TimeSeriesComputeNodeExecutor implements NodeExecutor<TimeSeriesComputeNodeConfigDTO> {
    private final NodeMetadataApplicationService nodeMetadataApplicationService;
    private final ComputeDatasetResolver computeDatasetResolver;
    private final InMemoryTimeSeriesComputeService timeSeriesComputeService;
    private final ComputeResultFactory computeResultFactory;

    public TimeSeriesComputeNodeExecutor(NodeMetadataApplicationService nodeMetadataApplicationService,
                                         ComputeDatasetResolver computeDatasetResolver,
                                         InMemoryTimeSeriesComputeService timeSeriesComputeService,
                                         ComputeResultFactory computeResultFactory) {
        this.nodeMetadataApplicationService = nodeMetadataApplicationService;
        this.computeDatasetResolver = computeDatasetResolver;
        this.timeSeriesComputeService = timeSeriesComputeService;
        this.computeResultFactory = computeResultFactory;
    }

    @Override
    public String supportType() {
        return "time_series_compute";
    }

    @Override
    public NodeResultDTO execute(NodeExecuteContextDTO context, TimeSeriesComputeNodeConfigDTO config) {
        long start = System.currentTimeMillis();
        DatasetDTO input = computeDatasetResolver.resolve(config.getDatasetRef(), context.getUpstreamResults());
        DatasetDTO output = timeSeriesComputeService.compute(config, input);
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
    public ValidationResultDTO validate(TimeSeriesComputeNodeConfigDTO config) {
        if (config == null || config.getTimeField() == null || config.getTimeField().isBlank()) {
            return ValidationResultDTO.builder().valid(false).errorMessage("timeField is required").build();
        }
        if (config.getGranularity() == null) {
            return ValidationResultDTO.builder().valid(false).errorMessage("granularity is required").build();
        }
        if (config.getMetrics() == null || config.getMetrics().isEmpty()) {
            return ValidationResultDTO.builder().valid(false).errorMessage("time series metrics are required").build();
        }
        for (MetricComputeRuleDTO metric : config.getMetrics()) {
            if (metric == null || metric.getMetricField() == null || metric.getMetricField().isBlank() || metric.getComputeType() == null) {
                return ValidationResultDTO.builder().valid(false).errorMessage("metricField and computeType are required").build();
            }
        }
        return ValidationResultDTO.builder().valid(true).build();
    }

    @Override
    public NodeMetaDTO metadata() {
        return nodeMetadataApplicationService.getNodeDefinition(supportType());
    }

    private ComputeAuditDTO buildAudit(TimeSeriesComputeNodeConfigDTO config, DatasetDTO input, DatasetDTO output) {
        List<ComputeStepDTO> steps = new ArrayList<>();
        steps.add(ComputeStepDTO.builder()
                .stepName("period_align")
                .description("按时间粒度对齐序列")
                .params(Map.of(
                        "timeField", config.getTimeField(),
                        "granularity", config.getGranularity().name(),
                        "dimensionFields", config.getDimensionFields() == null ? List.of() : config.getDimensionFields()))
                .build());
        if (config.getMetrics() != null) {
            for (MetricComputeRuleDTO metric : config.getMetrics()) {
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("metricField", metric.getMetricField());
                params.put("computeType", metric.getComputeType() == null ? null : metric.getComputeType().name());
                params.put("alias", metric.getAlias());
                params.put("compareShift", metric.getCompareShift());
                params.put("compareUnit", metric.getCompareUnit() == null ? null : metric.getCompareUnit().name());
                params.put("windowSize", metric.getWindowSize());
                steps.add(ComputeStepDTO.builder()
                        .stepName(metric.getComputeType() == null ? "time_series_compute" : metric.getComputeType().name().toLowerCase())
                        .description("执行时序指标计算")
                        .params(params)
                        .build());
            }
        }
        return ComputeAuditDTO.builder()
                .capabilityType(supportType())
                .steps(steps)
                .inputRowCount(rowCount(input))
                .outputRowCount(rowCount(output))
                .build();
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
