package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.Map;
import java.util.UUID;

import com.kuaishou.intelligentanalysisplatform.application.DatasourceApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.NodeMetadataApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.QueryApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeResultFactory;
import com.kuaishou.intelligentanalysisplatform.application.compute.InMemoryHashJoinService;
import com.kuaishou.intelligentanalysisplatform.application.compute.pushdown.DataJoinSqlGenerator;
import com.kuaishou.intelligentanalysisplatform.application.compute.pushdown.DatasourceDialect;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceType;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DataJoinNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ValidateResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecutor;
import com.kuaishou.intelligentanalysisplatform.contract.spi.ValidationResultDTO;
import org.springframework.stereotype.Component;

@Component
public class DataJoinNodeExecutor implements NodeExecutor<DataJoinNodeConfigDTO> {

    private static final int DEFAULT_ROW_LIMIT = 500_000;

    private final InMemoryHashJoinService hashJoinService;
    private final DataJoinSqlGenerator sqlGenerator;
    private final QueryApplicationService queryApplicationService;
    private final DatasourceApplicationService datasourceApplicationService;
    private final ComputeResultFactory computeResultFactory;
    private final NodeMetadataApplicationService nodeMetadataApplicationService;

    public DataJoinNodeExecutor(InMemoryHashJoinService hashJoinService,
                                DataJoinSqlGenerator sqlGenerator,
                                QueryApplicationService queryApplicationService,
                                DatasourceApplicationService datasourceApplicationService,
                                ComputeResultFactory computeResultFactory,
                                NodeMetadataApplicationService nodeMetadataApplicationService) {
        this.hashJoinService = hashJoinService;
        this.sqlGenerator = sqlGenerator;
        this.queryApplicationService = queryApplicationService;
        this.datasourceApplicationService = datasourceApplicationService;
        this.computeResultFactory = computeResultFactory;
        this.nodeMetadataApplicationService = nodeMetadataApplicationService;
    }

    @Override
    public String supportType() {
        return "data_join";
    }

    @Override
    public NodeResultDTO execute(NodeExecuteContextDTO context, DataJoinNodeConfigDTO config) {
        long start = System.currentTimeMillis();

        DatasetDTO left = extractDataset(context.getUpstreamResults(), config.getLeftDatasetRef());
        DatasetDTO right = extractDataset(context.getUpstreamResults(), config.getRightDatasetRef());

        DatasetDTO result;
        boolean pushdown = false;

        if (canSqlJoin(left, right)) {
            DatasourceType dsType = resolveDatasourceType(left.getSourceDatasourceId(), context);
            if (dsType != null) {
                DatasourceDialect dialect = DatasourceDialect.from(dsType);
                String joinSql = sqlGenerator.generate(
                        left.getSourceSql(), right.getSourceSql(),
                        config.getJoinType(), config.getOn(),
                        config.getSelectColumns(), dialect);
                QueryRequestDTO queryReq = QueryRequestDTO.builder()
                        .requestId(UUID.randomUUID().toString())
                        .datasourceId(left.getSourceDatasourceId())
                        .sql(joinSql)
                        .parameters(Map.of())
                        .context(context.getRequestContext())
                        .build();
                ValidateResultDTO validateResult = queryApplicationService.validate(queryReq);
                if (validateResult.isValid()) {
                    QueryResultDTO queryResult = queryApplicationService.preview(queryReq);
                    result = enrichSourceInfo(queryResult.getDataset(), joinSql, left.getSourceDatasourceId());
                    pushdown = true;
                } else {
                    result = executeInMemory(left, right, config);
                }
            } else {
                result = executeInMemory(left, right, config);
            }
        } else {
            result = executeInMemory(left, right, config);
        }

        return computeResultFactory.success(
                context.getNodeId(),
                supportType(),
                result,
                pushdown,
                supportType(),
                System.currentTimeMillis() - start);
    }

    @Override
    public ValidationResultDTO validate(DataJoinNodeConfigDTO config) {
        return ValidationResultDTO.builder().valid(true).build();
    }

    @Override
    public NodeMetaDTO metadata() {
        return nodeMetadataApplicationService.getNodeDefinition(supportType());
    }

    private DatasetDTO extractDataset(Map<String, StandardResultDTO> upstreamResults, String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT,
                    "DATA_JOIN requires leftDatasetRef and rightDatasetRef to be set");
        }
        StandardResultDTO result = upstreamResults.get(nodeId);
        if (result == null) {
            throw new BaseBusinessException(ErrorCode.NODE_NOT_FOUND,
                    "upstream node result not found for nodeId: " + nodeId);
        }
        if (result.getDataset() == null) {
            throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT,
                    "upstream node " + nodeId + " did not produce a dataset result");
        }
        return result.getDataset();
    }

    private boolean canSqlJoin(DatasetDTO left, DatasetDTO right) {
        return left.getSourceDatasourceId() != null
                && left.getSourceDatasourceId().equals(right.getSourceDatasourceId())
                && left.getSourceSql() != null
                && right.getSourceSql() != null;
    }

    private DatasetDTO executeInMemory(DatasetDTO left, DatasetDTO right, DataJoinNodeConfigDTO config) {
        int rowLimit = config.getMemoryRowLimit() != null ? config.getMemoryRowLimit() : DEFAULT_ROW_LIMIT;
        return hashJoinService.join(left, right, config.getJoinType(),
                config.getOn(), config.getSelectColumns(), rowLimit);
    }

    private DatasourceType resolveDatasourceType(String datasourceId, NodeExecuteContextDTO context) {
        try {
            DatasourceDTO ds = datasourceApplicationService.getById(datasourceId, context.getRequestContext());
            return ds == null ? null : ds.getType();
        } catch (Exception e) {
            return null;
        }
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
}
