package com.kuaishou.intelligentanalysisplatform.infra.repository;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.domain.task.AsyncTask;
import com.kuaishou.intelligentanalysisplatform.domain.task.TaskType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JdbcTest
@Import(JdbcAsyncTaskRepository.class)
@Sql("classpath:schema.sql")
class JdbcAsyncTaskRepositoryTest {
    @Autowired
    private JdbcAsyncTaskRepository repository;

    @Test
    void shouldSaveAndFindTask() {
        repository.save(AsyncTask.builder()
                .taskId("task-q1")
                .taskType(TaskType.QUERY)
                .refId("q1")
                .tenantId("t1")
                .operatorId("u1")
                .status(ExecutionStatus.QUEUED)
                .createdAt(1L)
                .updatedAt(1L)
                .build());

        AsyncTask task = repository.findById("task-q1").orElseThrow();
        assertEquals(TaskType.QUERY, task.getTaskType());
        assertEquals(ExecutionStatus.QUEUED, task.getStatus());
    }

    @Test
    void shouldUpdateTaskStatus() {
        repository.save(AsyncTask.builder()
                .taskId("task-q2")
                .taskType(TaskType.QUERY)
                .refId("q2")
                .tenantId("t1")
                .status(ExecutionStatus.QUEUED)
                .createdAt(1L)
                .updatedAt(1L)
                .build());

        repository.updateStatus("task-q2", ExecutionStatus.RUNNING, 2L, null, null);

        AsyncTask task = repository.findById("task-q2").orElseThrow();
        assertEquals(ExecutionStatus.RUNNING, task.getStatus());
        assertEquals(2L, task.getUpdatedAt());
        assertTrue(task.getErrorCode() == null);
    }
}
