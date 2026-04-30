package com.kuaishou.intelligentanalysisplatform.application.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceStatus;
import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceType;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ValidateResultDTO;
import com.kuaishou.intelligentanalysisplatform.domain.datasource.AnalysisDatasource;
import com.kuaishou.intelligentanalysisplatform.domain.datasource.DatasourceRepository;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.Connector;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.ConnectorFactory;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.QueryResult;
import com.kuaishou.intelligentanalysisplatform.domain.query.execution.QueryExecution;
import com.kuaishou.intelligentanalysisplatform.domain.query.execution.QueryExecutionRepository;
import com.kuaishou.intelligentanalysisplatform.domain.query.model.SqlGuardDecision;
import com.kuaishou.intelligentanalysisplatform.domain.query.schema.SchemaInferService;
import com.kuaishou.intelligentanalysisplatform.domain.query.service.SqlGuard;
import com.kuaishou.intelligentanalysisplatform.domain.task.AsyncTaskRepository;
import com.kuaishou.intelligentanalysisplatform.infra.observability.QueryAuditLogService;
import com.kuaishou.intelligentanalysisplatform.infra.observability.QueryGovernanceLimiter;
import com.kuaishou.intelligentanalysisplatform.infra.observability.QueryMetricsService;
import com.kuaishou.intelligentanalysisplatform.infra.query.cache.QueryCacheStore;
import com.kuaishou.intelligentanalysisplatform.infra.query.cancel.QueryCancellationRegistry;
import com.kuaishou.intelligentanalysisplatform.infra.query.executor.AsyncQueryExecutor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultQueryApplicationServiceTest {
    @Test
    void shouldReturnQueryResult() {
        SqlGuard sqlGuard = mock(SqlGuard.class);
        DatasourceRepository datasourceRepository = mock(DatasourceRepository.class);
        ConnectorFactory connectorFactory = mock(ConnectorFactory.class);
        QueryCacheStore queryCacheStore = mock(QueryCacheStore.class);
        QueryCancellationRegistry cancellationRegistry = mock(QueryCancellationRegistry.class);
        SchemaInferService schemaInferService = mock(SchemaInferService.class);
        QueryExecutionRepository queryExecutionRepository = mock(QueryExecutionRepository.class);
        AsyncTaskRepository asyncTaskRepository = mock(AsyncTaskRepository.class);
        AsyncQueryExecutor asyncQueryExecutor = mock(AsyncQueryExecutor.class);
        Connector connector = mock(Connector.class);
        when(sqlGuard.validate(any())).thenReturn(SqlGuardDecision.builder().allowed(true).normalizedSql("select 1").sqlFingerprint("fp-1").build());
        AnalysisDatasource datasource = AnalysisDatasource.builder().id("ds1").tenantId("t1").type(DatasourceType.MYSQL).status(DatasourceStatus.ACTIVE).build();
        when(datasourceRepository.findByIdAndTenantId("ds1", "t1")).thenReturn(Optional.of(datasource));
        when(connectorFactory.create(datasource)).thenReturn(connector);
        when(queryCacheStore.get(any())).thenReturn(Optional.empty());
        when(connector.execute(any(), any())).thenReturn(QueryResult.builder().fields(List.of()).rows(List.of(Map.of("id", 1))).rowCount(1).truncated(false).elapsedMs(1L).cached(false).build());
        DefaultQueryApplicationService service = newService(sqlGuard, datasourceRepository, connectorFactory, queryCacheStore, cancellationRegistry, schemaInferService, queryExecutionRepository, asyncTaskRepository, asyncQueryExecutor);
        QueryRequestDTO request = QueryRequestDTO.builder().requestId("q1").datasourceId("ds1").sql("select 1").context(RequestContextDTO.builder().tenantId("t1").userId("u1").build()).build();
        assertEquals(ExecutionStatus.SUCCEEDED, service.preview(request).getStatus());
    }

    @Test
    void shouldValidateQuery() {
        SqlGuard sqlGuard = mock(SqlGuard.class);
        DatasourceRepository datasourceRepository = mock(DatasourceRepository.class);
        ConnectorFactory connectorFactory = mock(ConnectorFactory.class);
        QueryCacheStore queryCacheStore = mock(QueryCacheStore.class);
        QueryCancellationRegistry cancellationRegistry = mock(QueryCancellationRegistry.class);
        SchemaInferService schemaInferService = mock(SchemaInferService.class);
        QueryExecutionRepository queryExecutionRepository = mock(QueryExecutionRepository.class);
        AsyncTaskRepository asyncTaskRepository = mock(AsyncTaskRepository.class);
        AsyncQueryExecutor asyncQueryExecutor = mock(AsyncQueryExecutor.class);
        when(sqlGuard.validate(any())).thenReturn(SqlGuardDecision.builder().allowed(true).normalizedSql("select 1").sqlFingerprint("fp-1").message("ok").build());
        when(datasourceRepository.findByIdAndTenantId("ds1", "t1")).thenReturn(Optional.of(AnalysisDatasource.builder().id("ds1").tenantId("t1").type(DatasourceType.MYSQL).status(DatasourceStatus.ACTIVE).build()));
        DefaultQueryApplicationService service = newService(sqlGuard, datasourceRepository, connectorFactory, queryCacheStore, cancellationRegistry, schemaInferService, queryExecutionRepository, asyncTaskRepository, asyncQueryExecutor);
        ValidateResultDTO result = service.validate(QueryRequestDTO.builder().requestId("q1").datasourceId("ds1").sql("select 1").context(RequestContextDTO.builder().tenantId("t1").userId("u1").build()).build());
        assertTrue(result.isValid());
        assertEquals("fp-1", result.getSqlFingerprint());
    }

    @Test
    void shouldSubmitAsyncQuery() {
        SqlGuard sqlGuard = mock(SqlGuard.class);
        DatasourceRepository datasourceRepository = mock(DatasourceRepository.class);
        ConnectorFactory connectorFactory = mock(ConnectorFactory.class);
        QueryCacheStore queryCacheStore = mock(QueryCacheStore.class);
        QueryCancellationRegistry cancellationRegistry = mock(QueryCancellationRegistry.class);
        SchemaInferService schemaInferService = mock(SchemaInferService.class);
        QueryExecutionRepository queryExecutionRepository = mock(QueryExecutionRepository.class);
        AsyncTaskRepository asyncTaskRepository = mock(AsyncTaskRepository.class);
        AsyncQueryExecutor asyncQueryExecutor = mock(AsyncQueryExecutor.class);
        when(sqlGuard.validate(any())).thenReturn(SqlGuardDecision.builder().allowed(true).normalizedSql("select 1").sqlFingerprint("fp-1").build());
        when(datasourceRepository.findByIdAndTenantId("ds1", "t1")).thenReturn(Optional.of(AnalysisDatasource.builder().id("ds1").tenantId("t1").type(DatasourceType.MYSQL).status(DatasourceStatus.ACTIVE).build()));
        DefaultQueryApplicationService service = newService(sqlGuard, datasourceRepository, connectorFactory, queryCacheStore, cancellationRegistry, schemaInferService, queryExecutionRepository, asyncTaskRepository, asyncQueryExecutor);
        QueryRequestDTO request = QueryRequestDTO.builder().requestId("q1").datasourceId("ds1").sql("select 1").context(RequestContextDTO.builder().tenantId("t1").userId("u1").build()).build();
        assertEquals("task-q1", service.runAsync(request).getTaskId());
        verify(asyncQueryExecutor).submit(any(QueryExecution.class), any(AnalysisDatasource.class), any(), any());
    }

    @Test
    void shouldReturnStatus() {
        DefaultQueryApplicationService service = newService(mock(SqlGuard.class), mock(DatasourceRepository.class), mock(ConnectorFactory.class), mock(QueryCacheStore.class), mock(QueryCancellationRegistry.class), mock(SchemaInferService.class), mock(QueryExecutionRepository.class), mock(AsyncTaskRepository.class), mock(AsyncQueryExecutor.class));
        QueryExecutionRepository queryExecutionRepository = mock(QueryExecutionRepository.class);
        when(queryExecutionRepository.findById("q1")).thenReturn(Optional.of(QueryExecution.builder().queryId("q1").mode("RUN").status(ExecutionStatus.RUNNING).startedAt(1L).cached(false).truncated(false).rowCount(0).build()));
        service = newService(mock(SqlGuard.class), mock(DatasourceRepository.class), mock(ConnectorFactory.class), mock(QueryCacheStore.class), mock(QueryCancellationRegistry.class), mock(SchemaInferService.class), queryExecutionRepository, mock(AsyncTaskRepository.class), mock(AsyncQueryExecutor.class));
        assertEquals(ExecutionStatus.RUNNING, service.getStatus("q1").getStatus());
    }

    @Test
    void shouldCancelQueuedExecutionWithoutStatement() {
        QueryCancellationRegistry cancellationRegistry = mock(QueryCancellationRegistry.class);
        QueryExecutionRepository queryExecutionRepository = mock(QueryExecutionRepository.class);
        when(queryExecutionRepository.findById("q1")).thenReturn(Optional.of(QueryExecution.builder().queryId("q1").status(ExecutionStatus.QUEUED).build()));
        when(cancellationRegistry.cancel("q1")).thenReturn(false);
        DefaultQueryApplicationService service = newService(mock(SqlGuard.class), mock(DatasourceRepository.class), mock(ConnectorFactory.class), mock(QueryCacheStore.class), cancellationRegistry, mock(SchemaInferService.class), queryExecutionRepository, mock(AsyncTaskRepository.class), mock(AsyncQueryExecutor.class));
        service.cancel("q1");
        verify(queryExecutionRepository).updateStatus(org.mockito.ArgumentMatchers.eq("q1"), org.mockito.ArgumentMatchers.eq(ExecutionStatus.CANCELLED), any(), any(), any());
    }

    @Test
    void shouldThrowWhenDatasourceMissing() {
        SqlGuard sqlGuard = mock(SqlGuard.class);
        DatasourceRepository datasourceRepository = mock(DatasourceRepository.class);
        when(sqlGuard.validate(any())).thenReturn(SqlGuardDecision.builder().allowed(true).normalizedSql("select 1").build());
        when(datasourceRepository.findByIdAndTenantId(any(), any())).thenReturn(Optional.empty());
        DefaultQueryApplicationService service = newService(sqlGuard, datasourceRepository, mock(ConnectorFactory.class), mock(QueryCacheStore.class), mock(QueryCancellationRegistry.class), mock(SchemaInferService.class), mock(QueryExecutionRepository.class), mock(AsyncTaskRepository.class), mock(AsyncQueryExecutor.class));
        QueryRequestDTO request = QueryRequestDTO.builder().requestId("q1").datasourceId("ds1").sql("select 1").context(RequestContextDTO.builder().tenantId("t1").userId("u1").build()).build();
        BaseBusinessException exception = assertThrows(BaseBusinessException.class, () -> service.preview(request));
        assertEquals(ErrorCode.DATASOURCE_NOT_FOUND, exception.getErrorCode());
    }

    private DefaultQueryApplicationService newService(SqlGuard sqlGuard,
                                                      DatasourceRepository datasourceRepository,
                                                      ConnectorFactory connectorFactory,
                                                      QueryCacheStore queryCacheStore,
                                                      QueryCancellationRegistry cancellationRegistry,
                                                      SchemaInferService schemaInferService,
                                                      QueryExecutionRepository queryExecutionRepository,
                                                      AsyncTaskRepository asyncTaskRepository,
                                                      AsyncQueryExecutor asyncQueryExecutor) {
        return new DefaultQueryApplicationService(
                sqlGuard,
                datasourceRepository,
                connectorFactory,
                queryCacheStore,
                cancellationRegistry,
                schemaInferService,
                queryExecutionRepository,
                asyncTaskRepository,
                asyncQueryExecutor,
                new QueryAuditLogService(new ObjectMapper()),
                new QueryMetricsService(),
                new QueryGovernanceLimiter(),
                10000,
                200,
                60,
                5000,
                20,
                10);
    }
}
