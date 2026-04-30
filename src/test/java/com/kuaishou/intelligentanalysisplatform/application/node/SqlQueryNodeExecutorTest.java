package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.application.NodeMetadataApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.QueryApplicationService;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ResultKind;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ValueSourceType;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ValueType;
import com.kuaishou.intelligentanalysisplatform.contract.schema.AsyncSubmitResponseDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeInputBindingDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeRunMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryOptionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryParameterDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SqlQueryNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.VariableRefDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SqlQueryNodeExecutorTest {
    @Test
    void shouldBuildSyncNodeResult() {
        QueryApplicationService queryApplicationService = mock(QueryApplicationService.class);
        NodeMetadataApplicationService nodeMetadataApplicationService = mock(NodeMetadataApplicationService.class);
        RuntimeBindingResolver runtimeBindingResolver = new RuntimeBindingResolver();
        SqlQueryNodeExecutor executor = new SqlQueryNodeExecutor(queryApplicationService, nodeMetadataApplicationService, runtimeBindingResolver);
        when(queryApplicationService.preview(any())).thenReturn(QueryResultDTO.builder()
                .status(ExecutionStatus.SUCCEEDED)
                .dataset(DatasetDTO.builder().rows(List.of(Map.of("id", 1))).build())
                .build());

        NodeResultDTO result = executor.execute(context(), SqlQueryNodeConfigDTO.builder()
                .datasourceId("ds1")
                .sqlTemplate("select 1")
                .build());

        assertEquals("sql_query", executor.supportType());
        assertEquals(ExecutionStatus.SUCCEEDED, result.getStatus());
        assertEquals(ResultKind.DATASET, result.getResult().getKind());
        assertEquals(1, result.getResult().getDataset().getRows().size());
    }

    @Test
    void shouldBuildAsyncNodeResult() {
        QueryApplicationService queryApplicationService = mock(QueryApplicationService.class);
        NodeMetadataApplicationService nodeMetadataApplicationService = mock(NodeMetadataApplicationService.class);
        SqlQueryNodeExecutor executor = new SqlQueryNodeExecutor(queryApplicationService, nodeMetadataApplicationService, new RuntimeBindingResolver());
        when(queryApplicationService.runAsync(any())).thenReturn(AsyncSubmitResponseDTO.builder()
                .taskId("task-q1")
                .status(ExecutionStatus.QUEUED)
                .pollUrl("/api/v1/tasks/task-q1")
                .build());

        NodeResultDTO result = executor.execute(context(), SqlQueryNodeConfigDTO.builder()
                .datasourceId("ds1")
                .sqlTemplate("select 1")
                .queryOption(QueryOptionDTO.builder().asyncPreferred(true).build())
                .build());

        assertEquals(ExecutionStatus.QUEUED, result.getStatus());
        assertEquals(ResultKind.VARIABLES, result.getResult().getKind());
        assertEquals("task-q1", result.getMeta().getTaskId());
    }

    @Test
    void shouldValidateConfig() {
        SqlQueryNodeExecutor executor = new SqlQueryNodeExecutor(mock(QueryApplicationService.class), mock(NodeMetadataApplicationService.class), new RuntimeBindingResolver());
        assertTrue(executor.validate(SqlQueryNodeConfigDTO.builder().datasourceId("ds1").sqlTemplate("select 1").build()).isValid());
        assertFalse(executor.validate(SqlQueryNodeConfigDTO.builder().datasourceId("ds1").build()).isValid());
    }

    @Test
    void shouldResolveVariableParameter() {
        SqlQueryNodeExecutor executor = new SqlQueryNodeExecutor(mock(QueryApplicationService.class), mock(NodeMetadataApplicationService.class), new RuntimeBindingResolver());
        QueryRequestDTO request = executor.buildQueryRequest(NodeExecuteContextDTO.builder()
                        .runId("run1")
                        .nodeId("node1")
                        .requestContext(RequestContextDTO.builder().tenantId("t1").build())
                        .upstreamResults(Map.of("upstream", StandardResultDTO.builder()
                                .kind(ResultKind.DATASET)
                                .dataset(DatasetDTO.builder().rows(List.of(Map.of("dt", "2026-04-29"))).build())
                                .build()))
                        .build(),
                SqlQueryNodeConfigDTO.builder()
                        .datasourceId("ds1")
                        .sqlTemplate("select * from table where dt = :dt")
                        .parameters(List.of(QueryParameterDTO.builder()
                                .name("dt")
                                .variableRef(VariableRefDTO.builder().sourceNodeId("upstream").path(List.of("dataset", "rows", "0", "dt")).build())
                                .build()))
                        .inputs(List.of(NodeInputBindingDTO.builder()
                                .name("limit")
                                .valueType(ValueType.INTEGER)
                                .sourceType(ValueSourceType.LITERAL)
                                .literalValue(10)
                                .build()))
                        .build());

        assertEquals("2026-04-29", request.getParameters().get("dt"));
        assertEquals(10, request.getParameters().get("limit"));
    }

    @Test
    void shouldExposeMetadata() {
        QueryApplicationService queryApplicationService = mock(QueryApplicationService.class);
        NodeMetadataApplicationService nodeMetadataApplicationService = mock(NodeMetadataApplicationService.class);
        when(nodeMetadataApplicationService.getNodeDefinition("sql_query")).thenReturn(NodeMetaDTO.builder().nodeType("sql_query").build());
        SqlQueryNodeExecutor executor = new SqlQueryNodeExecutor(queryApplicationService, nodeMetadataApplicationService, new RuntimeBindingResolver());
        assertEquals("sql_query", executor.metadata().getNodeType());
    }

    private NodeExecuteContextDTO context() {
        return NodeExecuteContextDTO.builder()
                .workflowId("wf1")
                .runId("run1")
                .nodeId("node1")
                .requestContext(RequestContextDTO.builder().tenantId("t1").userId("u1").build())
                .build();
    }
}
