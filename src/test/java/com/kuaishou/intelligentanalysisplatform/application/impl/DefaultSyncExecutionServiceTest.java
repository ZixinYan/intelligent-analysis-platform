package com.kuaishou.intelligentanalysisplatform.application.impl;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.application.node.NodeExecuteDispatcher;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ResultKind;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeDebugRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowNodeDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowRunRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowRunResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultSyncExecutionServiceTest {
    @Test
    void shouldRunNode() {
        NodeExecuteDispatcher dispatcher = mock(NodeExecuteDispatcher.class);
        WorkflowDagExecutor workflowDagExecutor = mock(WorkflowDagExecutor.class);
        DefaultSyncExecutionService service = new DefaultSyncExecutionService(dispatcher, workflowDagExecutor);
        NodeDebugRequestDTO request = NodeDebugRequestDTO.builder()
                .workflowId("wf1")
                .nodeId("node1")
                .node(WorkflowNodeDTO.builder().nodeId("node1").nodeType("sql_query").build())
                .build();
        when(dispatcher.buildContext(request)).thenReturn(NodeExecuteContextDTO.builder().nodeId("node1").build());
        when(dispatcher.dispatch(any(), any())).thenReturn(NodeResultDTO.builder().nodeId("node1").status(ExecutionStatus.SUCCEEDED).build());

        NodeResultDTO result = service.runNode(request);

        assertEquals(ExecutionStatus.SUCCEEDED, result.getStatus());
    }

    @Test
    void shouldRunWorkflowSequentially() {
        NodeExecuteDispatcher dispatcher = mock(NodeExecuteDispatcher.class);
        WorkflowDagExecutor workflowDagExecutor = mock(WorkflowDagExecutor.class);
        DefaultSyncExecutionService service = new DefaultSyncExecutionService(dispatcher, workflowDagExecutor);
        WorkflowNodeDTO node1 = WorkflowNodeDTO.builder().nodeId("node1").nodeType("sql_query").build();
        WorkflowNodeDTO node2 = WorkflowNodeDTO.builder().nodeId("node2").nodeType("sql_query").build();
        when(dispatcher.dispatch(any(), any()))
                .thenReturn(NodeResultDTO.builder().nodeId("node1").status(ExecutionStatus.SUCCEEDED).result(StandardResultDTO.builder().kind(ResultKind.DATASET).dataset(DatasetDTO.builder().rows(List.of()).build()).build()).build())
                .thenReturn(NodeResultDTO.builder().nodeId("node2").status(ExecutionStatus.SUCCEEDED).result(StandardResultDTO.builder().kind(ResultKind.DATASET).dataset(DatasetDTO.builder().rows(List.of()).build()).build()).build());

        assertEquals(2, service.runWorkflow(WorkflowRunRequestDTO.builder().workflowId("wf1").nodes(List.of(node1, node2)).build()).getNodeResults().size());
    }

    @Test
    void shouldPassUpstreamResultsToNextNode() {
        NodeExecuteDispatcher dispatcher = mock(NodeExecuteDispatcher.class);
        WorkflowDagExecutor workflowDagExecutor = mock(WorkflowDagExecutor.class);
        DefaultSyncExecutionService service = new DefaultSyncExecutionService(dispatcher, workflowDagExecutor);
        WorkflowNodeDTO node1 = WorkflowNodeDTO.builder().nodeId("node1").nodeType("sql_query").build();
        WorkflowNodeDTO node2 = WorkflowNodeDTO.builder().nodeId("node2").nodeType("table_output").build();
        StandardResultDTO firstResult = StandardResultDTO.builder()
                .kind(ResultKind.DATASET)
                .dataset(DatasetDTO.builder().rows(List.of(java.util.Map.of("id", 1))).build())
                .build();
        StandardResultDTO secondResult = StandardResultDTO.builder()
                .kind(ResultKind.DATASET)
                .dataset(DatasetDTO.builder().rows(List.of(java.util.Map.of("id", 2))).build())
                .build();
        when(dispatcher.dispatch(any(), any()))
                .thenAnswer(invocation -> {
                    NodeExecuteContextDTO context = invocation.getArgument(1);
                    assertNotNull(context.getUpstreamResults());
                    assertEquals(0, context.getUpstreamResults().size());
                    return NodeResultDTO.builder().nodeId("node1").status(ExecutionStatus.SUCCEEDED).result(firstResult).build();
                })
                .thenAnswer(invocation -> {
                    NodeExecuteContextDTO context = invocation.getArgument(1);
                    assertEquals(firstResult, context.getUpstreamResults().get("node1"));
                    return NodeResultDTO.builder().nodeId("node2").status(ExecutionStatus.SUCCEEDED).result(secondResult).build();
                });

        WorkflowRunResultDTO result = service.runWorkflow(WorkflowRunRequestDTO.builder()
                .workflowId("wf1")
                .nodes(List.of(node1, node2))
                .build());

        ArgumentCaptor<NodeExecuteContextDTO> contextCaptor = ArgumentCaptor.forClass(NodeExecuteContextDTO.class);
        verify(dispatcher, times(2)).dispatch(any(), contextCaptor.capture());
        List<NodeExecuteContextDTO> contexts = contextCaptor.getAllValues();
        assertEquals(firstResult, contexts.get(1).getUpstreamResults().get("node1"));
        assertEquals(ExecutionStatus.SUCCEEDED, result.getStatus());
        assertEquals(secondResult, result.getFinalResult());
    }

    @Test
    void shouldStopWorkflowWhenNodeFailedAndKeepCompletedResults() {
        NodeExecuteDispatcher dispatcher = mock(NodeExecuteDispatcher.class);
        WorkflowDagExecutor workflowDagExecutor = mock(WorkflowDagExecutor.class);
        DefaultSyncExecutionService service = new DefaultSyncExecutionService(dispatcher, workflowDagExecutor);
        WorkflowNodeDTO node1 = WorkflowNodeDTO.builder().nodeId("node1").nodeType("sql_query").build();
        WorkflowNodeDTO node2 = WorkflowNodeDTO.builder().nodeId("node2").nodeType("filter").build();
        WorkflowNodeDTO node3 = WorkflowNodeDTO.builder().nodeId("node3").nodeType("table_output").build();
        StandardResultDTO firstResult = StandardResultDTO.builder()
                .kind(ResultKind.DATASET)
                .dataset(DatasetDTO.builder().rows(List.of(java.util.Map.of("id", 1))).build())
                .build();
        when(dispatcher.dispatch(any(), any()))
                .thenReturn(NodeResultDTO.builder().nodeId("node1").status(ExecutionStatus.SUCCEEDED).result(firstResult).build())
                .thenReturn(NodeResultDTO.builder().nodeId("node2").status(ExecutionStatus.FAILED).build());

        WorkflowRunResultDTO result = service.runWorkflow(WorkflowRunRequestDTO.builder()
                .workflowId("wf1")
                .nodes(List.of(node1, node2, node3))
                .build());

        verify(dispatcher, times(2)).dispatch(any(), any());
        assertEquals(ExecutionStatus.FAILED, result.getStatus());
        assertEquals(2, result.getNodeResults().size());
        assertEquals("node1", result.getNodeResults().get(0).getNodeId());
        assertEquals("node2", result.getNodeResults().get(1).getNodeId());
        assertEquals(firstResult, result.getFinalResult());
        assertNull(result.getNodeResults().stream().filter(item -> "node3".equals(item.getNodeId())).findAny().orElse(null));
    }
}
