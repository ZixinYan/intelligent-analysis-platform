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
import com.kuaishou.intelligentanalysisplatform.application.compute.InMemoryFilterComputeService;
import com.kuaishou.intelligentanalysisplatform.application.compute.pushdown.DatasourceDialect;
import com.kuaishou.intelligentanalysisplatform.application.compute.pushdown.FilterSqlGenerator;
import com.kuaishou.intelligentanalysisplatform.application.compute.pushdown.PushdownDecider;
import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceType;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ComputeAuditDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ComputeStepDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FilterConditionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FilterNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ValidateResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecutor;
import com.kuaishou.intelligentanalysisplatform.contract.spi.ValidationResultDTO;
import org.springframework.stereotype.Component;

@Component
public class FilterNodeExecutor implements NodeExecutor<FilterNodeConfigDTO> {
    private final NodeMetadataApplicationService nodeMetadataApplicationService;
    private final ComputeDatasetResolver computeDatasetResolver;
    private final InMemoryFilterComputeService filterComputeService;
    private final ComputeResultFactory computeResultFactory;
    private final PushdownDecider pushdownDecider;
    private final ComputeCapabilityRegistry capabilityRegistry;
    private final FilterSqlGenerator filterSqlGenerator;
    private final QueryApplicationService queryApplicationService;
    private final DatasourceApplicationService datasourceApplicationService;

    public FilterNodeExecutor(NodeMetadataApplicationService nodeMetadataApplicationService,
                              ComputeDatasetResolver computeDatasetResolver,
                              InMemoryFilterComputeService filterComputeService,
                              ComputeResultFactory computeResultFactory,
                              PushdownDecider pushdownDecider,
                              ComputeCapabilityRegistry capabilityRegistry,
                              FilterSqlGenerator filterSqlGenerator,
                              QueryApplicationService queryApplicationService,
                              DatasourceApplicationService datasourceApplicationService) {
        this.nodeMetadataApplicationService = nodeMetadataApplicationService;
        this.computeDatasetResolver = computeDatasetResolver;
        this.filterComputeService = filterComputeService;
        this.computeResultFactory = computeResultFactory;
        this.pushdownDecider = pushdownDecider;
        this.capabilityRegistry = capabilityRegistry;
        this.filterSqlGenerator = filterSqlGenerator;
        this.queryApplicationService = queryApplicationService;
        this.datasourceApplicationService = datasourceApplicationService;
    }

    @Override
    public String supportType() {
        return "filter";
    }

    @Override
    public NodeResultDTO execute(NodeExecuteContextDTO context, FilterNodeConfigDTO config) {
        long start = System.currentTimeMillis();
        DatasetDTO input = computeDatasetResolver.resolve(config.getDatasetRef(), context.getUpstreamResults());

        DatasourceType dsType = resolveDatasourceType(input, context);
        boolean pushdown = pushdownDecider.canPushdown(capabilityRegistry.getByCode("filter"), input, dsType);

        DatasetDTO output;
        if (pushdown) {
            DatasourceDialect dialect = DatasourceDialect.from(dsType);
            String pushdownSql = filterSqlGenerator.generate(input.getSourceSql(), config, dialect);
            QueryRequestDTO queryReq = buildPushdownRequest(input.getSourceDatasourceId(), pushdownSql, context);
            ValidateResultDTO validateResult = queryApplicationService.validate(queryReq);
            if (validateResult.isValid()) {
                QueryResultDTO result = queryApplicationService.preview(queryReq);
                output = enrichSourceInfo(result.getDataset(), pushdownSql, input.getSourceDatasourceId());
            } else {
                // SQL Guard 拒绝，回退到内存计算
                pushdown = false;
                output = filterComputeService.compute(config, input);
            }
        } else {
            output = filterComputeService.compute(config, input);
        }

        return computeResultFactory.success(
                context.getNodeId(),
                supportType(),
                output,
                pushdown,
                supportType(),
                System.currentTimeMillis() - start,
                buildAudit(config, input, output)
        );
    }

    @Override
    public ValidationResultDTO validate(FilterNodeConfigDTO config) {
        return ValidationResultDTO.builder().valid(true).build();
    }

    @Override
    public NodeMetaDTO metadata() {
        return nodeMetadataApplicationService.getNodeDefinition(supportType());
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

    private ComputeAuditDTO buildAudit(FilterNodeConfigDTO config, DatasetDTO input, DatasetDTO output) {
        List<Map<String, Object>> conditions = new ArrayList<>();
        if (config.getConditions() != null) {
            for (FilterConditionDTO condition : config.getConditions()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("field", condition.getField());
                item.put("operator", condition.getOperator() == null ? null : condition.getOperator().name());
                item.put("value", condition.getValue());
                item.put("values", condition.getValues());
                conditions.add(item);
            }
        }
        return ComputeAuditDTO.builder()
                .capabilityType(supportType())
                .steps(List.of(ComputeStepDTO.builder()
                        .stepName("filter")
                        .description("按条件过滤数据")
                        .params(Map.of("conditions", conditions))
                        .build()))
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
