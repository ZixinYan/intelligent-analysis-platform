package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.kuaishou.intelligentanalysisplatform.application.NodeMetadataApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.QueryApplicationService;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ResultKind;
import com.kuaishou.intelligentanalysisplatform.contract.schema.AsyncSubmitResponseDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeRunMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryParameterDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SqlQueryNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecutor;
import com.kuaishou.intelligentanalysisplatform.contract.spi.ValidationResultDTO;
import org.springframework.stereotype.Component;

@Component
public class SqlQueryNodeExecutor implements NodeExecutor<SqlQueryNodeConfigDTO> {
    private final QueryApplicationService queryApplicationService;
    private final NodeMetadataApplicationService nodeMetadataApplicationService;
    private final RuntimeBindingResolver runtimeBindingResolver;

    public SqlQueryNodeExecutor(QueryApplicationService queryApplicationService,
                                NodeMetadataApplicationService nodeMetadataApplicationService,
                                RuntimeBindingResolver runtimeBindingResolver) {
        this.queryApplicationService = queryApplicationService;
        this.nodeMetadataApplicationService = nodeMetadataApplicationService;
        this.runtimeBindingResolver = runtimeBindingResolver;
    }

    @Override
    public String supportType() {
        return "sql_query";
    }

    @Override
    public NodeResultDTO execute(NodeExecuteContextDTO context, SqlQueryNodeConfigDTO config) {
        QueryRequestDTO request = buildQueryRequest(context, config);
        long start = System.currentTimeMillis();
        if (Boolean.TRUE.equals(config.getQueryOption() == null ? null : config.getQueryOption().getAsyncPreferred())) {
            AsyncSubmitResponseDTO asyncResponse = queryApplicationService.runAsync(request);
            return NodeResultDTO.builder()
                    .nodeId(context.getNodeId())
                    .nodeType(supportType())
                    .status(asyncResponse.getStatus())
                    .result(StandardResultDTO.builder()
                            .kind(ResultKind.VARIABLES)
                            .variables(Map.of(
                                    "taskId", asyncResponse.getTaskId(),
                                    "pollUrl", asyncResponse.getPollUrl(),
                                    "status", asyncResponse.getStatus().name()))
                            .build())
                    .meta(NodeRunMetaDTO.builder()
                            .elapsedMs(System.currentTimeMillis() - start)
                            .taskId(asyncResponse.getTaskId())
                            .build())
                    .build();
        }
        QueryResultDTO result = queryApplicationService.preview(request);
        return NodeResultDTO.builder()
                .nodeId(context.getNodeId())
                .nodeType(supportType())
                .status(result.getStatus())
                .result(StandardResultDTO.builder()
                        .kind(ResultKind.DATASET)
                        .dataset(result.getDataset())
                        .build())
                .meta(NodeRunMetaDTO.builder()
                        .elapsedMs(System.currentTimeMillis() - start)
                        .cached(result.getExecutionMeta() == null ? null : result.getExecutionMeta().getCached())
                        .build())
                .build();
    }

    @Override
    public ValidationResultDTO validate(SqlQueryNodeConfigDTO config) {
        if (config == null) {
            return ValidationResultDTO.builder().valid(false).errorMessage("sql query config is required").build();
        }
        if (config.getDatasourceId() == null || config.getDatasourceId().isBlank()) {
            return ValidationResultDTO.builder().valid(false).errorMessage("datasourceId is required").build();
        }
        if (config.getSqlTemplate() == null || config.getSqlTemplate().isBlank()) {
            return ValidationResultDTO.builder().valid(false).errorMessage("sqlTemplate is required").build();
        }
        return ValidationResultDTO.builder().valid(true).build();
    }

    @Override
    public com.kuaishou.intelligentanalysisplatform.contract.schema.NodeMetaDTO metadata() {
        return nodeMetadataApplicationService.getNodeDefinition(supportType());
    }

    public QueryRequestDTO buildQueryRequest(NodeExecuteContextDTO context, SqlQueryNodeConfigDTO config) {
        Map<String, Object> parameters = new LinkedHashMap<>(runtimeBindingResolver.resolve(config.getInputs(), context.getUpstreamResults()));
        if (config.getParameters() != null) {
            for (QueryParameterDTO parameter : config.getParameters()) {
                if (parameter == null || parameter.getName() == null || parameter.getName().isBlank()) {
                    continue;
                }
                Object value = parameter.getDefaultValue();
                if (parameter.getVariableRef() != null) {
                    Object variableValue = runtimeBindingResolver.resolveVariable(parameter.getVariableRef(), context.getUpstreamResults());
                    value = variableValue == null ? parameter.getDefaultValue() : variableValue;
                }
                parameters.put(parameter.getName(), value);
            }
        }
        RequestContextDTO requestContext = context.getRequestContext();
        return QueryRequestDTO.builder()
                .requestId(resolveRequestId(context))
                .datasourceId(config.getDatasourceId())
                .sql(config.getSqlTemplate())
                .parameters(parameters)
                .option(config.getQueryOption())
                .context(requestContext)
                .build();
    }

    private String resolveRequestId(NodeExecuteContextDTO context) {
        if (context.getRunId() != null && !context.getRunId().isBlank()) {
            return context.getRunId() + "-" + context.getNodeId();
        }
        return UUID.randomUUID().toString();
    }
}
