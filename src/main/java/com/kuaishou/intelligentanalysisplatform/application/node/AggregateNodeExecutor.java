package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kuaishou.intelligentanalysisplatform.application.DatasourceApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.NodeMetadataApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.QueryApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeCapabilityRegistry;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeDatasetResolver;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeResultFactory;
import com.kuaishou.intelligentanalysisplatform.application.compute.InMemoryAggregateComputeService;
import com.kuaishou.intelligentanalysisplatform.application.compute.pushdown.AggregateSqlGenerator;
import com.kuaishou.intelligentanalysisplatform.application.compute.pushdown.DatasourceDialect;
import com.kuaishou.intelligentanalysisplatform.application.compute.pushdown.PushdownDecider;
import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceType;
import com.kuaishou.intelligentanalysisplatform.contract.enums.AggregateFunction;
import com.kuaishou.intelligentanalysisplatform.contract.schema.AggregateMetricDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.AggregateNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ComputeAuditDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ComputeStepDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SortFieldDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ValidateResultDTO;
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
    private final PushdownDecider pushdownDecider;
    private final ComputeCapabilityRegistry capabilityRegistry;
    private final AggregateSqlGenerator aggregateSqlGenerator;
    private final QueryApplicationService queryApplicationService;
    private final DatasourceApplicationService datasourceApplicationService;

    public AggregateNodeExecutor(NodeMetadataApplicationService nodeMetadataApplicationService,
                                 ComputeDatasetResolver computeDatasetResolver,
                                 InMemoryAggregateComputeService aggregateComputeService,
                                 ComputeResultFactory computeResultFactory,
                                 PushdownDecider pushdownDecider,
                                 ComputeCapabilityRegistry capabilityRegistry,
                                 AggregateSqlGenerator aggregateSqlGenerator,
                                 QueryApplicationService queryApplicationService,
                                 DatasourceApplicationService datasourceApplicationService) {
        this.nodeMetadataApplicationService = nodeMetadataApplicationService;
        this.computeDatasetResolver = computeDatasetResolver;
        this.aggregateComputeService = aggregateComputeService;
        this.computeResultFactory = computeResultFactory;
        this.pushdownDecider = pushdownDecider;
        this.capabilityRegistry = capabilityRegistry;
        this.aggregateSqlGenerator = aggregateSqlGenerator;
        this.queryApplicationService = queryApplicationService;
        this.datasourceApplicationService = datasourceApplicationService;
    }

    @Override
    public String supportType() {
        return "aggregate";
    }

    @Override
    public NodeResultDTO execute(NodeExecuteContextDTO context, AggregateNodeConfigDTO config) {
        long start = System.currentTimeMillis();
        AggregateNodeConfigDTO normalized = normalizeMetrics(config);
        DatasetDTO input = computeDatasetResolver.resolve(normalized.getDatasetRef(), context.getUpstreamResults());

        DatasourceType dsType = resolveDatasourceType(input, context);
        boolean pushdown = pushdownDecider.canPushdown(capabilityRegistry.getByCode("aggregate"), input, dsType);

        DatasetDTO output;
        if (pushdown) {
            DatasourceDialect dialect = DatasourceDialect.from(dsType);
            String pushdownSql = aggregateSqlGenerator.generate(input.getSourceSql(), normalized, dialect);
            QueryRequestDTO queryReq = buildPushdownRequest(input.getSourceDatasourceId(), pushdownSql, context);
            ValidateResultDTO validateResult = queryApplicationService.validate(queryReq);
            if (validateResult.isValid()) {
                QueryResultDTO result = queryApplicationService.preview(queryReq);
                output = enrichSourceInfo(result.getDataset(), pushdownSql, input.getSourceDatasourceId());
            } else {
                pushdown = false;
                output = aggregateComputeService.compute(normalized, input);
            }
        } else {
            output = aggregateComputeService.compute(normalized, input);
        }

        return computeResultFactory.success(
                context.getNodeId(),
                supportType(),
                output,
                pushdown,
                supportType(),
                System.currentTimeMillis() - start,
                buildAudit(normalized, input, output)
        );
    }

    @Override
    public ValidationResultDTO validate(AggregateNodeConfigDTO config) {
        if (config == null) {
            return ValidationResultDTO.builder().valid(false).errorMessage("aggregate config is required").build();
        }
        // 接受 metrics 列表 或 扁平字段 metricField + aggregateFunc
        boolean hasMetricsList = config.getMetrics() != null && !config.getMetrics().isEmpty();
        boolean hasFlatField = config.getMetricField() != null && !config.getMetricField().isBlank();
        if (!hasMetricsList && !hasFlatField) {
            return ValidationResultDTO.builder().valid(false)
                    .errorMessage("请选择聚合字段（metricField）").build();
        }
        if (hasMetricsList) {
            for (AggregateMetricDTO metric : config.getMetrics()) {
                if (metric == null || metric.getField() == null || metric.getField().isBlank() || metric.getAgg() == null) {
                    return ValidationResultDTO.builder().valid(false)
                            .errorMessage("aggregate metric field and agg are required").build();
                }
            }
        }
        return ValidationResultDTO.builder().valid(true).build();
    }

    @Override
    public NodeMetaDTO metadata() {
        return nodeMetadataApplicationService.getNodeDefinition(supportType());
    }

    /**
     * 将扁平字段（metricField / aggregateFunc / metricAlias）转换为 metrics 列表。
     * 若 metrics 已有值则直接返回原 config，避免重复构建。
     */
    private AggregateNodeConfigDTO normalizeMetrics(AggregateNodeConfigDTO config) {
        if (config.getMetrics() != null && !config.getMetrics().isEmpty()) {
            return config;
        }
        if (config.getMetricField() == null || config.getMetricField().isBlank()) {
            return config;
        }
        AggregateFunction agg = AggregateFunction.SUM;
        if (config.getAggregateFunc() != null && !config.getAggregateFunc().isBlank()) {
            try {
                agg = AggregateFunction.valueOf(config.getAggregateFunc().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // 未知函数降级为 SUM
            }
        }
        AggregateMetricDTO metric = AggregateMetricDTO.builder()
                .field(config.getMetricField())
                .agg(agg)
                .alias(config.getMetricAlias())
                .build();
        return AggregateNodeConfigDTO.builder()
                .datasetRef(config.getDatasetRef())
                .groupByFields(config.getGroupByFields())
                .metrics(List.of(metric))
                .sortFields(config.getSortFields())
                .topN(config.getTopN())
                .pushdownEnabled(config.getPushdownEnabled())
                .metricField(config.getMetricField())
                .aggregateFunc(config.getAggregateFunc())
                .metricAlias(config.getMetricAlias())
                .build();
    }

    private DatasourceType resolveDatasourceType(DatasetDTO input, NodeExecuteContextDTO context) {
        if (input == null || input.getSourceDatasourceId() == null || input.getSourceDatasourceId().isBlank()) {
            return null;
        }
        try {
            DatasourceDTO ds = datasourceApplicationService.getById(input.getSourceDatasourceId(), context.getRequestContext());
            return ds == null ? null : ds.getType();
        } catch (Exception e) {
            return null;
        }
    }

    private QueryRequestDTO buildPushdownRequest(String datasourceId, String sql, NodeExecuteContextDTO context) {
        return QueryRequestDTO.builder()
                .requestId(UUID.randomUUID().toString())
                .datasourceId(datasourceId)
                .sql(sql)
                .parameters(Map.of())
                .context(context.getRequestContext())
                .build();
    }

    private DatasetDTO enrichSourceInfo(DatasetDTO dataset, String sourceSql, String sourceDatasourceId) {
        if (dataset == null) {
            return null;
        }
        return DatasetDTO.builder()
                .schema(dataset.getSchema())
                .rows(dataset.getRows())
                .page(dataset.getPage())
                .stat(dataset.getStat())
                .sourceSql(sourceSql)
                .sourceDatasourceId(sourceDatasourceId)
                .build();
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
