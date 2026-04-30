package com.kuaishou.intelligentanalysisplatform.infra.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.domain.query.execution.QueryExecution;
import com.kuaishou.intelligentanalysisplatform.domain.task.AsyncTask;
import com.kuaishou.intelligentanalysisplatform.domain.task.TaskType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class QueryAuditLogServiceTest {
    @Test
    void shouldLogWithoutSensitivePayloadFailure() {
        QueryAuditLogService service = new QueryAuditLogService(new ObjectMapper());
        assertThatCode(() -> service.logQuery(QueryExecution.builder()
                .queryId("q1")
                .tenantId("tenant-1")
                .datasourceId("ds-1")
                .sqlFingerprint("fp")
                .mode("RUN")
                .status(ExecutionStatus.SUCCEEDED)
                .elapsedMs(100L)
                .rowCount(10)
                .cached(false)
                .truncated(false)
                .build(), true)).doesNotThrowAnyException();
        assertThatCode(() -> service.logTask(AsyncTask.builder()
                .taskId("task-q1")
                .taskType(TaskType.QUERY)
                .refId("q1")
                .tenantId("tenant-1")
                .status(ExecutionStatus.SUCCEEDED)
                .updatedAt(1L)
                .build())).doesNotThrowAnyException();
    }
}
