package com.kuaishou.intelligentanalysisplatform.infra.query.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceType;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.domain.datasource.AnalysisDatasource;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.Connector;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.ConnectorFactory;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.QueryCommand;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.QueryResult;
import com.kuaishou.intelligentanalysisplatform.domain.query.execution.QueryExecution;
import com.kuaishou.intelligentanalysisplatform.domain.query.execution.QueryExecutionRepository;
import com.kuaishou.intelligentanalysisplatform.domain.task.AsyncTaskRepository;
import com.kuaishou.intelligentanalysisplatform.domain.task.TaskResultRepository;
import com.kuaishou.intelligentanalysisplatform.infra.observability.QueryAuditLogService;
import com.kuaishou.intelligentanalysisplatform.infra.observability.QueryGovernanceLimiter;
import com.kuaishou.intelligentanalysisplatform.infra.observability.QueryMetricsService;
import com.kuaishou.intelligentanalysisplatform.infra.query.cancel.QueryCancellationRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsyncQueryExecutorTest {
    @Test
    void shouldPersistSucceededStatus() {
        ConnectorFactory connectorFactory = mock(ConnectorFactory.class);
        QueryExecutionRepository repository = mock(QueryExecutionRepository.class);
        AsyncTaskRepository asyncTaskRepository = mock(AsyncTaskRepository.class);
        TaskResultRepository taskResultRepository = mock(TaskResultRepository.class);
        QueryCancellationRegistry registry = mock(QueryCancellationRegistry.class);
        Connector connector = mock(Connector.class);
        when(connectorFactory.create(any())).thenReturn(connector);
        when(connector.execute(any(), any())).thenReturn(QueryResult.builder().fields(List.of()).rows(List.of(Map.of("id", 1))).rowCount(1).truncated(false).elapsedMs(1L).cached(false).build());
        AsyncQueryExecutor executor = new AsyncQueryExecutor(
                connectorFactory,
                repository,
                asyncTaskRepository,
                taskResultRepository,
                registry,
                new QueryAuditLogService(new ObjectMapper()),
                new QueryMetricsService(),
                new ObjectMapper(),
                5000,
                1,
                1,
                10);
        executor.submit(
                QueryExecution.builder().queryId("q1").tenantId("tenant-1").datasourceId("ds1").mode("RUN").build(),
                AnalysisDatasource.builder().id("ds1").type(DatasourceType.MYSQL).build(),
                QueryCommand.builder().queryId("q1").normalizedSql("select 1").build(),
                new QueryGovernanceLimiter().acquire("tenant-1", 10, "ds1", 1));
        verify(repository).save(any(QueryExecution.class));
        verify(repository, timeout(1000)).updateStatus(eq("q1"), eq(ExecutionStatus.RUNNING), eq(null), eq(null), eq(null));
        verify(asyncTaskRepository, timeout(1000)).updateStatus(eq("task-q1"), eq(ExecutionStatus.RUNNING), org.mockito.ArgumentMatchers.anyLong(), eq(null), eq(null));
        verify(repository, timeout(1000)).updateResult(eq("q1"), eq(ExecutionStatus.SUCCEEDED), any(), eq(1L), eq(false), eq(false), eq(1));
        verify(taskResultRepository, timeout(1000)).save(any());
        verify(asyncTaskRepository, timeout(1000)).updateStatus(eq("task-q1"), eq(ExecutionStatus.SUCCEEDED), org.mockito.ArgumentMatchers.anyLong(), eq(null), eq(null));
        verify(registry, timeout(1000)).deregister("q1");
    }
}
