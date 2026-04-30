package com.kuaishou.intelligentanalysisplatform.infra.repository;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.domain.query.execution.QueryExecution;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JdbcTest
@Import(JdbcQueryExecutionRepository.class)
@Sql("classpath:schema.sql")
class JdbcQueryExecutionRepositoryTest {
    @Autowired
    private JdbcQueryExecutionRepository repository;

    @Test
    void shouldSaveAndFindExecution() {
        repository.save(QueryExecution.builder()
                .queryId("q1")
                .tenantId("t1")
                .datasourceId("ds1")
                .sqlFingerprint("fp1")
                .mode("RUN")
                .status(ExecutionStatus.QUEUED)
                .startedAt(1L)
                .cached(false)
                .truncated(false)
                .rowCount(0)
                .operatorId("u1")
                .createdAt(1L)
                .build());
        Optional<QueryExecution> execution = repository.findById("q1");
        assertTrue(execution.isPresent());
        assertEquals(ExecutionStatus.QUEUED, execution.get().getStatus());
    }

    @Test
    void shouldUpdateExecutionResult() {
        repository.save(QueryExecution.builder()
                .queryId("q2")
                .tenantId("t1")
                .datasourceId("ds1")
                .mode("RUN")
                .status(ExecutionStatus.QUEUED)
                .startedAt(1L)
                .cached(false)
                .truncated(false)
                .rowCount(0)
                .createdAt(1L)
                .build());
        repository.updateResult("q2", ExecutionStatus.SUCCEEDED, 3L, 2L, false, false, 10);
        QueryExecution execution = repository.findById("q2").orElseThrow();
        assertEquals(ExecutionStatus.SUCCEEDED, execution.getStatus());
        assertEquals(10, execution.getRowCount());
    }
}
