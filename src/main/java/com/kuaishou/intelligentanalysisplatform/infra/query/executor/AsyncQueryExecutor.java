package com.kuaishou.intelligentanalysisplatform.infra.query.executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetPageDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetStatDTO;
import com.kuaishou.intelligentanalysisplatform.domain.datasource.AnalysisDatasource;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.Connector;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.ConnectorFactory;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.QueryCommand;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.QueryResult;
import com.kuaishou.intelligentanalysisplatform.domain.query.execution.QueryExecution;
import com.kuaishou.intelligentanalysisplatform.domain.query.execution.QueryExecutionRepository;
import com.kuaishou.intelligentanalysisplatform.domain.task.AsyncTask;
import com.kuaishou.intelligentanalysisplatform.domain.task.AsyncTaskRepository;
import com.kuaishou.intelligentanalysisplatform.domain.task.TaskResult;
import com.kuaishou.intelligentanalysisplatform.domain.task.TaskResultRepository;
import com.kuaishou.intelligentanalysisplatform.domain.task.TaskType;
import com.kuaishou.intelligentanalysisplatform.infra.observability.QueryAuditLogService;
import com.kuaishou.intelligentanalysisplatform.infra.observability.QueryGovernanceLimiter;
import com.kuaishou.intelligentanalysisplatform.infra.observability.QueryMetricsService;
import com.kuaishou.intelligentanalysisplatform.infra.query.cancel.QueryCancellationRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class AsyncQueryExecutor {
    private final ConnectorFactory connectorFactory;
    private final QueryExecutionRepository queryExecutionRepository;
    private final AsyncTaskRepository asyncTaskRepository;
    private final TaskResultRepository taskResultRepository;
    private final QueryCancellationRegistry queryCancellationRegistry;
    private final QueryAuditLogService queryAuditLogService;
    private final QueryMetricsService queryMetricsService;
    private final ObjectMapper objectMapper;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final int slowQueryThresholdMs;

    public AsyncQueryExecutor(ConnectorFactory connectorFactory,
                              QueryExecutionRepository queryExecutionRepository,
                              AsyncTaskRepository asyncTaskRepository,
                              TaskResultRepository taskResultRepository,
                              QueryCancellationRegistry queryCancellationRegistry,
                              QueryAuditLogService queryAuditLogService,
                              QueryMetricsService queryMetricsService,
                              ObjectMapper objectMapper,
                              @Value("${analysis.governance.slow-query-threshold-ms:5000}") int slowQueryThresholdMs,
                              @Value("${analysis.execution.async.core-pool-size:4}") int corePoolSize,
                              @Value("${analysis.execution.async.max-pool-size:8}") int maxPoolSize,
                              @Value("${analysis.execution.async.queue-capacity:100}") int queueCapacity) {
        this.connectorFactory = connectorFactory;
        this.queryExecutionRepository = queryExecutionRepository;
        this.asyncTaskRepository = asyncTaskRepository;
        this.taskResultRepository = taskResultRepository;
        this.queryCancellationRegistry = queryCancellationRegistry;
        this.queryAuditLogService = queryAuditLogService;
        this.queryMetricsService = queryMetricsService;
        this.objectMapper = objectMapper;
        this.slowQueryThresholdMs = slowQueryThresholdMs;
        this.taskExecutor = new ThreadPoolTaskExecutor();
        this.taskExecutor.setThreadNamePrefix("analysis-query-");
        this.taskExecutor.setCorePoolSize(corePoolSize);
        this.taskExecutor.setMaxPoolSize(maxPoolSize);
        this.taskExecutor.setQueueCapacity(queueCapacity);
        this.taskExecutor.initialize();
    }

    public void submit(QueryExecution execution,
                       AnalysisDatasource datasource,
                       QueryCommand command,
                       QueryGovernanceLimiter.Lease lease) {
        queryExecutionRepository.save(execution);
        queryMetricsService.updateAsyncQueueDepth(taskExecutor.getThreadPoolExecutor().getQueue().size());
        taskExecutor.submit(() -> runExecution(execution, datasource, command, lease));
    }

    private void runExecution(QueryExecution execution,
                              AnalysisDatasource datasource,
                              QueryCommand command,
                              QueryGovernanceLimiter.Lease lease) {
        String taskId = toTaskId(execution.getQueryId());
        queryExecutionRepository.updateStatus(execution.getQueryId(), ExecutionStatus.RUNNING, null, null, null);
        asyncTaskRepository.updateStatus(taskId, ExecutionStatus.RUNNING, System.currentTimeMillis(), null, null);
        try {
            Connector connector = connectorFactory.create(datasource);
            QueryResult result = connector.execute(datasource, command);
            long finishedAt = System.currentTimeMillis();
            long elapsedMs = result.getElapsedMs() == null ? 0L : result.getElapsedMs();
            queryExecutionRepository.updateResult(
                    execution.getQueryId(),
                    ExecutionStatus.SUCCEEDED,
                    finishedAt,
                    elapsedMs,
                    Boolean.TRUE.equals(result.getCached()),
                    Boolean.TRUE.equals(result.getTruncated()),
                    result.getRowCount() == null ? 0 : result.getRowCount());
            taskResultRepository.save(TaskResult.builder()
                    .taskId(taskId)
                    .resultJson(writeResultJson(result))
                    .createdAt(finishedAt)
                    .build());
            AsyncTask task = AsyncTask.builder()
                    .taskId(taskId)
                    .taskType(TaskType.QUERY)
                    .refId(execution.getQueryId())
                    .tenantId(execution.getTenantId())
                    .operatorId(execution.getOperatorId())
                    .status(ExecutionStatus.SUCCEEDED)
                    .createdAt(execution.getCreatedAt())
                    .updatedAt(finishedAt)
                    .build();
            asyncTaskRepository.updateStatus(taskId, ExecutionStatus.SUCCEEDED, finishedAt, null, null);
            QueryExecution audited = QueryExecution.builder()
                    .queryId(execution.getQueryId())
                    .tenantId(execution.getTenantId())
                    .datasourceId(execution.getDatasourceId())
                    .sqlFingerprint(execution.getSqlFingerprint())
                    .mode(execution.getMode())
                    .status(ExecutionStatus.SUCCEEDED)
                    .startedAt(finishedAt - elapsedMs)
                    .finishedAt(finishedAt)
                    .elapsedMs(elapsedMs)
                    .cached(Boolean.TRUE.equals(result.getCached()))
                    .truncated(Boolean.TRUE.equals(result.getTruncated()))
                    .rowCount(result.getRowCount() == null ? 0 : result.getRowCount())
                    .operatorId(execution.getOperatorId())
                    .createdAt(execution.getCreatedAt())
                    .build();
            boolean slowQuery = elapsedMs >= slowQueryThresholdMs;
            queryAuditLogService.logQuery(audited, slowQuery);
            queryAuditLogService.logTask(task);
            queryMetricsService.recordQuery(ExecutionStatus.SUCCEEDED, elapsedMs, slowQuery, execution.getDatasourceId());
        } catch (IllegalStateException exception) {
            ErrorCode errorCode = resolveErrorCode(exception);
            ExecutionStatus status = errorCode == ErrorCode.QUERY_CANCELLED ? ExecutionStatus.CANCELLED : ExecutionStatus.FAILED;
            long finishedAt = System.currentTimeMillis();
            queryExecutionRepository.updateStatus(
                    execution.getQueryId(),
                    status,
                    finishedAt,
                    errorCode.getCode(),
                    exception.getMessage());
            asyncTaskRepository.updateStatus(taskId, status, finishedAt, errorCode.getCode(), exception.getMessage());
            QueryExecution audited = QueryExecution.builder()
                    .queryId(execution.getQueryId())
                    .tenantId(execution.getTenantId())
                    .datasourceId(execution.getDatasourceId())
                    .sqlFingerprint(execution.getSqlFingerprint())
                    .mode(execution.getMode())
                    .status(status)
                    .startedAt(execution.getStartedAt())
                    .finishedAt(finishedAt)
                    .elapsedMs(finishedAt - (execution.getStartedAt() == null ? finishedAt : execution.getStartedAt()))
                    .cached(false)
                    .truncated(false)
                    .rowCount(0)
                    .errorCode(errorCode.getCode())
                    .errorMessage(exception.getMessage())
                    .operatorId(execution.getOperatorId())
                    .createdAt(execution.getCreatedAt())
                    .build();
            AsyncTask task = AsyncTask.builder()
                    .taskId(taskId)
                    .taskType(TaskType.QUERY)
                    .refId(execution.getQueryId())
                    .tenantId(execution.getTenantId())
                    .operatorId(execution.getOperatorId())
                    .status(status)
                    .errorCode(errorCode.getCode())
                    .errorMessage(exception.getMessage())
                    .createdAt(execution.getCreatedAt())
                    .updatedAt(finishedAt)
                    .build();
            boolean slowQuery = audited.getElapsedMs() != null && audited.getElapsedMs() >= slowQueryThresholdMs;
            queryAuditLogService.logQuery(audited, slowQuery);
            queryAuditLogService.logTask(task);
            queryMetricsService.recordQuery(status, audited.getElapsedMs(), slowQuery, execution.getDatasourceId());
            if (errorCode == ErrorCode.QUERY_TIMEOUT) {
                queryMetricsService.recordTimeout(execution.getDatasourceId());
            }
        } finally {
            queryCancellationRegistry.deregister(execution.getQueryId());
            if (lease != null) {
                lease.close();
            }
            queryMetricsService.updateDatasourceConcurrency(0);
            queryMetricsService.updateAsyncQueueDepth(taskExecutor.getThreadPoolExecutor().getQueue().size());
        }
    }

    private String writeResultJson(QueryResult result) {
        DatasetDTO dataset = DatasetDTO.builder()
                .schema(DatasetSchemaDTO.builder()
                        .fields(result.getFields())
                        .metrics(List.of())
                        .dimensions(List.of())
                        .timeFields(List.of())
                        .build())
                .rows(result.getRows() == null ? List.of() : result.getRows())
                .page(DatasetPageDTO.builder()
                        .pageSize(result.getRows() == null ? 0 : result.getRows().size())
                        .currentPage(1)
                        .total(result.getRowCount() == null ? 0L : result.getRowCount().longValue())
                        .nextCursor(result.getNextCursor())
                        .build())
                .stat(DatasetStatDTO.builder()
                        .rowCount(result.getRowCount())
                        .returnedRowCount(result.getRows() == null ? 0 : result.getRows().size())
                        .truncated(Boolean.TRUE.equals(result.getTruncated()))
                        .build())
                .build();
        try {
            return objectMapper.writeValueAsString(dataset);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("serialize task result failed", exception);
        }
    }

    private String toTaskId(String queryId) {
        return "task-" + queryId;
    }

    private ErrorCode resolveErrorCode(IllegalStateException exception) {
        String message = exception.getMessage();
        if (message != null && message.toLowerCase().contains("cancel")) {
            return ErrorCode.QUERY_CANCELLED;
        }
        Throwable cause = exception.getCause();
        if (cause instanceof java.sql.SQLTimeoutException) {
            return ErrorCode.QUERY_TIMEOUT;
        }
        if (cause != null && cause.getMessage() != null && cause.getMessage().toLowerCase().contains("cancel")) {
            return ErrorCode.QUERY_CANCELLED;
        }
        return ErrorCode.DOWNSTREAM_ERROR;
    }
}
