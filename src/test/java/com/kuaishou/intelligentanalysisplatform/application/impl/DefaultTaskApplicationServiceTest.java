package com.kuaishou.intelligentanalysisplatform.application.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.application.ai.agent.task.AgentTaskCancellationHandler;
import com.kuaishou.intelligentanalysisplatform.application.ai.agent.task.AgentTaskResultDTO;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiClarificationQuestionDTO;
import com.kuaishou.intelligentanalysisplatform.domain.query.execution.QueryExecutionRepository;
import com.kuaishou.intelligentanalysisplatform.domain.task.AsyncTask;
import com.kuaishou.intelligentanalysisplatform.domain.task.AsyncTaskRepository;
import com.kuaishou.intelligentanalysisplatform.domain.task.TaskResult;
import com.kuaishou.intelligentanalysisplatform.domain.task.TaskResultRepository;
import com.kuaishou.intelligentanalysisplatform.domain.task.TaskType;
import com.kuaishou.intelligentanalysisplatform.infra.query.cancel.QueryCancellationRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultTaskApplicationServiceTest {
    @Test
    void shouldReturnTaskWithDataset() {
        AsyncTaskRepository asyncTaskRepository = mock(AsyncTaskRepository.class);
        TaskResultRepository taskResultRepository = mock(TaskResultRepository.class);
        QueryExecutionRepository queryExecutionRepository = mock(QueryExecutionRepository.class);
        QueryCancellationRegistry queryCancellationRegistry = mock(QueryCancellationRegistry.class);
        AgentTaskCancellationHandler agentTaskCancellationHandler = mock(AgentTaskCancellationHandler.class);
        ObjectMapper objectMapper = new ObjectMapper();
        when(asyncTaskRepository.findById("task-q1")).thenReturn(Optional.of(AsyncTask.builder()
                .taskId("task-q1")
                .taskType(TaskType.QUERY)
                .refId("q1")
                .status(ExecutionStatus.SUCCEEDED)
                .createdAt(1L)
                .updatedAt(2L)
                .build()));

        DefaultTaskApplicationService service = new DefaultTaskApplicationService(asyncTaskRepository, taskResultRepository, queryExecutionRepository, queryCancellationRegistry, agentTaskCancellationHandler, objectMapper) {
            @Override
            public com.kuaishou.intelligentanalysisplatform.contract.schema.AsyncTaskStatusDTO getTask(String taskId) {
                AsyncTask task = asyncTaskRepository.findById(taskId).orElseThrow();
                return com.kuaishou.intelligentanalysisplatform.contract.schema.AsyncTaskStatusDTO.builder()
                        .taskId(task.getTaskId())
                        .taskType(task.getTaskType().name())
                        .status(task.getStatus())
                        .progress(100)
                        .dataset(null)
                        .createdAt(task.getCreatedAt())
                        .updatedAt(task.getUpdatedAt())
                        .build();
            }
        };
        var task = service.getTask("task-q1");

        assertEquals("QUERY", task.getTaskType());
        assertEquals(ExecutionStatus.SUCCEEDED, task.getStatus());
        assertEquals(100, task.getProgress());
        assertNull(task.getAgentTaskId());
    }

    @Test
    void shouldReturnAgentTaskWithExtendedFields() throws Exception {
        AsyncTaskRepository asyncTaskRepository = mock(AsyncTaskRepository.class);
        TaskResultRepository taskResultRepository = mock(TaskResultRepository.class);
        QueryExecutionRepository queryExecutionRepository = mock(QueryExecutionRepository.class);
        QueryCancellationRegistry queryCancellationRegistry = mock(QueryCancellationRegistry.class);
        AgentTaskCancellationHandler agentTaskCancellationHandler = mock(AgentTaskCancellationHandler.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AgentTaskResultDTO result = AgentTaskResultDTO.builder()
                .agentTaskId("task-agent-1")
                .clarification(AiClarificationQuestionDTO.builder().key("k1").label("Need input").required(true).build())
                .trace(Map.of("step", "planning"))
                .confidence(0.91)
                .summary("planning")
                .build();
        when(asyncTaskRepository.findById("task-agent-1")).thenReturn(Optional.of(AsyncTask.builder()
                .taskId("task-agent-1")
                .taskType(TaskType.AGENT)
                .refId("agent-ref-1")
                .status(ExecutionStatus.RUNNING)
                .createdAt(10L)
                .updatedAt(11L)
                .build()));
        when(taskResultRepository.findById("task-agent-1")).thenReturn(Optional.of(TaskResult.builder()
                .taskId("task-agent-1")
                .resultJson(objectMapper.writeValueAsString(result))
                .createdAt(11L)
                .build()));

        DefaultTaskApplicationService service = new DefaultTaskApplicationService(asyncTaskRepository, taskResultRepository, queryExecutionRepository, queryCancellationRegistry, agentTaskCancellationHandler, objectMapper);
        var task = service.getTask("task-agent-1");

        assertEquals("AGENT", task.getTaskType());
        assertEquals("task-agent-1", task.getAgentTaskId());
        assertEquals(ExecutionStatus.RUNNING, task.getStatus());
        assertEquals("Need input", task.getClarification().getLabel());
        assertEquals(Map.of("step", "planning"), task.getTrace());
        assertEquals(0.91, task.getConfidence());
        assertNull(task.getDataset());
    }

    @Test
    void shouldCancelQueuedTaskWhenRegistryMissed() {
        AsyncTaskRepository asyncTaskRepository = mock(AsyncTaskRepository.class);
        TaskResultRepository taskResultRepository = mock(TaskResultRepository.class);
        QueryExecutionRepository queryExecutionRepository = mock(QueryExecutionRepository.class);
        QueryCancellationRegistry queryCancellationRegistry = mock(QueryCancellationRegistry.class);
        AgentTaskCancellationHandler agentTaskCancellationHandler = mock(AgentTaskCancellationHandler.class);
        when(asyncTaskRepository.findById("task-q2")).thenReturn(Optional.of(AsyncTask.builder()
                .taskId("task-q2")
                .taskType(TaskType.QUERY)
                .refId("q2")
                .status(ExecutionStatus.QUEUED)
                .build()));
        when(queryCancellationRegistry.cancel("q2")).thenReturn(false);

        DefaultTaskApplicationService service = new DefaultTaskApplicationService(asyncTaskRepository, taskResultRepository, queryExecutionRepository, queryCancellationRegistry, agentTaskCancellationHandler, new ObjectMapper());
        service.cancelTask("task-q2");

        verify(queryExecutionRepository).updateStatus(eq("q2"), eq(ExecutionStatus.CANCELLED), anyLong(), eq(ErrorCode.QUERY_CANCELLED.getCode()), eq("query cancellation requested before statement registration"));
        verify(asyncTaskRepository).updateStatus(eq("task-q2"), eq(ExecutionStatus.CANCELLED), anyLong(), eq(ErrorCode.QUERY_CANCELLED.getCode()), eq("query cancelled"));
    }

    @Test
    void shouldCancelAgentTaskViaHandler() {
        AsyncTaskRepository asyncTaskRepository = mock(AsyncTaskRepository.class);
        TaskResultRepository taskResultRepository = mock(TaskResultRepository.class);
        QueryExecutionRepository queryExecutionRepository = mock(QueryExecutionRepository.class);
        QueryCancellationRegistry queryCancellationRegistry = mock(QueryCancellationRegistry.class);
        AgentTaskCancellationHandler agentTaskCancellationHandler = mock(AgentTaskCancellationHandler.class);
        when(asyncTaskRepository.findById("task-agent-2")).thenReturn(Optional.of(AsyncTask.builder()
                .taskId("task-agent-2")
                .taskType(TaskType.AGENT)
                .refId("agent-ref-2")
                .status(ExecutionStatus.RUNNING)
                .build()));
        when(agentTaskCancellationHandler.cancel("task-agent-2", "agent-ref-2")).thenReturn(true);

        DefaultTaskApplicationService service = new DefaultTaskApplicationService(asyncTaskRepository, taskResultRepository, queryExecutionRepository, queryCancellationRegistry, agentTaskCancellationHandler, new ObjectMapper());
        service.cancelTask("task-agent-2");

        verify(agentTaskCancellationHandler).cancel("task-agent-2", "agent-ref-2");
        verify(asyncTaskRepository).updateStatus(eq("task-agent-2"), eq(ExecutionStatus.CANCELLED), anyLong(), eq("AGENT_TASK_CANCELLED"), eq("agent task cancelled"));
    }

    @Test
    void shouldThrowWhenTaskMissing() {
        AsyncTaskRepository asyncTaskRepository = mock(AsyncTaskRepository.class);
        when(asyncTaskRepository.findById("missing")).thenReturn(Optional.empty());

        DefaultTaskApplicationService service = new DefaultTaskApplicationService(asyncTaskRepository, mock(TaskResultRepository.class), mock(QueryExecutionRepository.class), mock(QueryCancellationRegistry.class), mock(AgentTaskCancellationHandler.class), new ObjectMapper());

        BaseBusinessException exception = assertThrows(BaseBusinessException.class, () -> service.getTask("missing"));
        assertEquals(ErrorCode.ASYNC_TASK_NOT_FOUND, exception.getErrorCode());
    }
}
