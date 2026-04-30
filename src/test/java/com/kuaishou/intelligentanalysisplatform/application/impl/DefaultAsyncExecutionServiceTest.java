package com.kuaishou.intelligentanalysisplatform.application.impl;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.application.QueryApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.node.NodeExecuteDispatcher;
import com.kuaishou.intelligentanalysisplatform.application.node.SqlQueryNodeExecutor;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.schema.AsyncSubmitResponseDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeDebugRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryExecutionMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SqlQueryNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowNodeDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowRunRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultAsyncExecutionServiceTest {
    @Test
    void shouldSubmitNode() {
        SqlQueryNodeExecutor executor = mock(SqlQueryNodeExecutor.class);
        NodeExecuteDispatcher dispatcher = mock(NodeExecuteDispatcher.class);
        QueryApplicationService queryApplicationService = mock(QueryApplicationService.class);
        DefaultAsyncExecutionService service = new DefaultAsyncExecutionService(executor, dispatcher, queryApplicationService);
        NodeDebugRequestDTO request = NodeDebugRequestDTO.builder()
                .workflowId("wf1")
                .nodeId("node1")
                .node(WorkflowNodeDTO.builder().nodeId("node1").nodeType("sql_query").config(SqlQueryNodeConfigDTO.builder().datasourceId("ds1").sqlTemplate("select 1").build()).build())
                .build();
        when(dispatcher.buildContext(request)).thenReturn(NodeExecuteContextDTO.builder().nodeId("node1").build());
        when(executor.buildQueryRequest(any(), any())).thenReturn(com.kuaishou.intelligentanalysisplatform.contract.schema.QueryRequestDTO.builder().requestId("q1").build());
        when(queryApplicationService.runAsync(any())).thenReturn(AsyncSubmitResponseDTO.builder().taskId("task-q1").status(ExecutionStatus.QUEUED).build());

        assertEquals("task-q1", service.submitNode(request).getTaskId());
    }

    @Test
    void shouldSubmitSingleNodeWorkflow() {
        SqlQueryNodeExecutor executor = mock(SqlQueryNodeExecutor.class);
        NodeExecuteDispatcher dispatcher = mock(NodeExecuteDispatcher.class);
        QueryApplicationService queryApplicationService = mock(QueryApplicationService.class);
        DefaultAsyncExecutionService service = new DefaultAsyncExecutionService(executor, dispatcher, queryApplicationService);
        WorkflowNodeDTO node = WorkflowNodeDTO.builder().nodeId("node1").nodeType("sql_query").config(SqlQueryNodeConfigDTO.builder().datasourceId("ds1").sqlTemplate("select 1").build()).build();
        when(dispatcher.buildContext(any())).thenReturn(NodeExecuteContextDTO.builder().nodeId("node1").build());
        when(executor.buildQueryRequest(any(), any())).thenReturn(com.kuaishou.intelligentanalysisplatform.contract.schema.QueryRequestDTO.builder().requestId("q1").build());
        when(queryApplicationService.runAsync(any())).thenReturn(AsyncSubmitResponseDTO.builder().taskId("task-q1").status(ExecutionStatus.QUEUED).build());

        assertEquals("task-q1", service.submitWorkflow(WorkflowRunRequestDTO.builder().workflowId("wf1").nodes(List.of(node)).build()).getTaskId());
    }

    @Test
    void shouldQueryTaskStatusAndCancel() {
        SqlQueryNodeExecutor executor = mock(SqlQueryNodeExecutor.class);
        NodeExecuteDispatcher dispatcher = mock(NodeExecuteDispatcher.class);
        QueryApplicationService queryApplicationService = mock(QueryApplicationService.class);
        DefaultAsyncExecutionService service = new DefaultAsyncExecutionService(executor, dispatcher, queryApplicationService);
        when(queryApplicationService.getStatus("q1")).thenReturn(QueryResultDTO.builder()
                .status(ExecutionStatus.RUNNING)
                .executionMeta(QueryExecutionMetaDTO.builder().startedAt(1L).finishedAt(2L).build())
                .build());
        doNothing().when(queryApplicationService).cancel("q1");

        assertEquals(ExecutionStatus.RUNNING, service.getTask("task-q1").getStatus());
        service.cancelTask("task-q1");
        verify(queryApplicationService).cancel("q1");
    }
}
