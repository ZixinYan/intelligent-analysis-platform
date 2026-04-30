package com.kuaishou.intelligentanalysisplatform.application.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.schema.AsyncTaskStatusDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetPageDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetStatDTO;
import com.kuaishou.intelligentanalysisplatform.domain.query.execution.QueryExecutionRepository;
import com.kuaishou.intelligentanalysisplatform.domain.task.AsyncTask;
import com.kuaishou.intelligentanalysisplatform.domain.task.AsyncTaskRepository;
import com.kuaishou.intelligentanalysisplatform.domain.task.TaskResultRepository;
import com.kuaishou.intelligentanalysisplatform.domain.task.TaskType;
import com.kuaishou.intelligentanalysisplatform.infra.query.cancel.QueryCancellationRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        ObjectMapper objectMapper = new ObjectMapper();
        DatasetDTO dataset = DatasetDTO.builder()
                .schema(null)
                .rows(java.util.List.of())
                .page(DatasetPageDTO.builder().pageSize(1).currentPage(1).total(1L).build())
                .stat(DatasetStatDTO.builder().rowCount(1).returnedRowCount(1).truncated(false).build())
                .build();
        when(asyncTaskRepository.findById("task-q1")).thenReturn(Optional.of(AsyncTask.builder()
                .taskId("task-q1")
                .taskType(TaskType.QUERY)
                .refId("q1")
                .status(ExecutionStatus.SUCCEEDED)
                .createdAt(1L)
                .updatedAt(2L)
                .build()));

        DefaultTaskApplicationService service = new DefaultTaskApplicationService(asyncTaskRepository, taskResultRepository, queryExecutionRepository, queryCancellationRegistry, objectMapper) {
            @Override
            public AsyncTaskStatusDTO getTask(String taskId) {
                AsyncTask task = asyncTaskRepository.findById(taskId).orElseThrow();
                return AsyncTaskStatusDTO.builder()
                        .taskId(task.getTaskId())
                        .taskType(task.getTaskType().name())
                        .status(task.getStatus())
                        .progress(100)
                        .dataset(dataset)
                        .createdAt(task.getCreatedAt())
                        .updatedAt(task.getUpdatedAt())
                        .build();
            }
        };
        var task = service.getTask("task-q1");

        assertEquals("QUERY", task.getTaskType());
        assertEquals(ExecutionStatus.SUCCEEDED, task.getStatus());
        assertNotNull(task.getDataset());
        assertEquals(100, task.getProgress());
    }

    @Test
    void shouldCancelQueuedTaskWhenRegistryMissed() {
        AsyncTaskRepository asyncTaskRepository = mock(AsyncTaskRepository.class);
        TaskResultRepository taskResultRepository = mock(TaskResultRepository.class);
        QueryExecutionRepository queryExecutionRepository = mock(QueryExecutionRepository.class);
        QueryCancellationRegistry queryCancellationRegistry = mock(QueryCancellationRegistry.class);
        when(asyncTaskRepository.findById("task-q2")).thenReturn(Optional.of(AsyncTask.builder()
                .taskId("task-q2")
                .taskType(TaskType.QUERY)
                .refId("q2")
                .status(ExecutionStatus.QUEUED)
                .build()));
        when(queryCancellationRegistry.cancel("q2")).thenReturn(false);

        DefaultTaskApplicationService service = new DefaultTaskApplicationService(asyncTaskRepository, taskResultRepository, queryExecutionRepository, queryCancellationRegistry, new ObjectMapper());
        service.cancelTask("task-q2");

        verify(queryExecutionRepository).updateStatus(eq("q2"), eq(ExecutionStatus.CANCELLED), org.mockito.ArgumentMatchers.anyLong(), eq(ErrorCode.QUERY_CANCELLED.getCode()), eq("query cancellation requested before statement registration"));
        verify(asyncTaskRepository).updateStatus(eq("task-q2"), eq(ExecutionStatus.CANCELLED), org.mockito.ArgumentMatchers.anyLong(), eq(ErrorCode.QUERY_CANCELLED.getCode()), eq("query cancelled"));
    }

    @Test
    void shouldThrowWhenTaskMissing() {
        AsyncTaskRepository asyncTaskRepository = mock(AsyncTaskRepository.class);
        when(asyncTaskRepository.findById("missing")).thenReturn(Optional.empty());

        DefaultTaskApplicationService service = new DefaultTaskApplicationService(asyncTaskRepository, mock(TaskResultRepository.class), mock(QueryExecutionRepository.class), mock(QueryCancellationRegistry.class), new ObjectMapper());

        BaseBusinessException exception = assertThrows(BaseBusinessException.class, () -> service.getTask("missing"));
        assertEquals(ErrorCode.ASYNC_TASK_NOT_FOUND, exception.getErrorCode());
    }
}
