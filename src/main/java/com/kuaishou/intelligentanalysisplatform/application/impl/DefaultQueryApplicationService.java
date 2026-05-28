package com.kuaishou.intelligentanalysisplatform.application.impl;

import java.sql.SQLTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kuaishou.intelligentanalysisplatform.application.QueryApplicationService;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorInfoDTO;
import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceStatus;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.schema.AsyncSubmitResponseDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetPageDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetStatDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryExecutionMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryOptionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SchemaInferResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ValidateResultDTO;
import com.kuaishou.intelligentanalysisplatform.domain.datasource.AnalysisDatasource;
import com.kuaishou.intelligentanalysisplatform.domain.datasource.DatasourceRepository;
import com.kuaishou.intelligentanalysisplatform.domain.query.SqlGuardRejectedException;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.Connector;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.ConnectorFactory;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.PaginationMode;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.QueryCommand;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.QueryResult;
import com.kuaishou.intelligentanalysisplatform.domain.query.execution.QueryExecution;
import com.kuaishou.intelligentanalysisplatform.domain.query.execution.QueryExecutionRepository;
import com.kuaishou.intelligentanalysisplatform.domain.query.model.GuardViolationCode;
import com.kuaishou.intelligentanalysisplatform.domain.query.model.QueryGovernancePolicy;
import com.kuaishou.intelligentanalysisplatform.domain.query.model.QueryGuardContext;
import com.kuaishou.intelligentanalysisplatform.domain.query.model.SqlGuardDecision;
import com.kuaishou.intelligentanalysisplatform.domain.query.schema.SchemaInferService;
import com.kuaishou.intelligentanalysisplatform.domain.query.service.SqlGuard;
import com.kuaishou.intelligentanalysisplatform.domain.task.AsyncTask;
import com.kuaishou.intelligentanalysisplatform.domain.task.AsyncTaskRepository;
import com.kuaishou.intelligentanalysisplatform.domain.task.TaskType;
import com.kuaishou.intelligentanalysisplatform.infra.observability.QueryAuditLogService;
import com.kuaishou.intelligentanalysisplatform.infra.observability.QueryGovernanceLimiter;
import com.kuaishou.intelligentanalysisplatform.infra.observability.QueryMetricsService;
import com.kuaishou.intelligentanalysisplatform.infra.query.cache.QueryCacheKeyBuilder;
import com.kuaishou.intelligentanalysisplatform.infra.query.cache.QueryCacheStore;
import com.kuaishou.intelligentanalysisplatform.infra.query.cancel.QueryCancellationRegistry;
import com.kuaishou.intelligentanalysisplatform.infra.query.executor.AsyncQueryExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DefaultQueryApplicationService implements QueryApplicationService {
    private static final Logger log = LoggerFactory.getLogger(DefaultQueryApplicationService.class);
    private static final String MODE_PREVIEW = "PREVIEW";
    private static final String MODE_RUN = "RUN";

    private final SqlGuard sqlGuard;
    private final DatasourceRepository datasourceRepository;
    private final ConnectorFactory connectorFactory;
    private final QueryCacheStore queryCacheStore;
    private final QueryCancellationRegistry queryCancellationRegistry;
    private final SchemaInferService schemaInferService;
    private final QueryExecutionRepository queryExecutionRepository;
    private final AsyncTaskRepository asyncTaskRepository;
    private final AsyncQueryExecutor asyncQueryExecutor;
    private final QueryAuditLogService queryAuditLogService;
    private final QueryMetricsService queryMetricsService;
    private final QueryGovernanceLimiter queryGovernanceLimiter;
    private final int defaultTimeoutMs;
    private final int defaultPageSize;
    private final int defaultCacheTtlSeconds;
    private final int slowQueryThresholdMs;
    private final int tenantQpsLimit;
    private final int datasourceConcurrencyLimit;

    public DefaultQueryApplicationService(SqlGuard sqlGuard,
                                          DatasourceRepository datasourceRepository,
                                          ConnectorFactory connectorFactory,
                                          QueryCacheStore queryCacheStore,
                                          QueryCancellationRegistry queryCancellationRegistry,
                                          SchemaInferService schemaInferService,
                                          QueryExecutionRepository queryExecutionRepository,
                                          AsyncTaskRepository asyncTaskRepository,
                                          AsyncQueryExecutor asyncQueryExecutor,
                                          QueryAuditLogService queryAuditLogService,
                                          QueryMetricsService queryMetricsService,
                                          QueryGovernanceLimiter queryGovernanceLimiter,
                                          @Value("${connector.query.default-timeout-ms:10000}") int defaultTimeoutMs,
                                          @Value("${connector.query.default-page-size:200}") int defaultPageSize,
                                          @Value("${connector.query.default-cache-ttl-seconds:60}") int defaultCacheTtlSeconds,
                                          @Value("${analysis.governance.slow-query-threshold-ms:5000}") int slowQueryThresholdMs,
                                          @Value("${analysis.governance.tenant-qps-limit:20}") int tenantQpsLimit,
                                          @Value("${analysis.governance.datasource-concurrency-limit:10}") int datasourceConcurrencyLimit) {
        this.sqlGuard = sqlGuard;
        this.datasourceRepository = datasourceRepository;
        this.connectorFactory = connectorFactory;
        this.queryCacheStore = queryCacheStore;
        this.queryCancellationRegistry = queryCancellationRegistry;
        this.schemaInferService = schemaInferService;
        this.queryExecutionRepository = queryExecutionRepository;
        this.asyncTaskRepository = asyncTaskRepository;
        this.asyncQueryExecutor = asyncQueryExecutor;
        this.queryAuditLogService = queryAuditLogService;
        this.queryMetricsService = queryMetricsService;
        this.queryGovernanceLimiter = queryGovernanceLimiter;
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.defaultPageSize = defaultPageSize;
        this.defaultCacheTtlSeconds = defaultCacheTtlSeconds;
        this.slowQueryThresholdMs = slowQueryThresholdMs;
        this.tenantQpsLimit = tenantQpsLimit;
        this.datasourceConcurrencyLimit = datasourceConcurrencyLimit;
    }

    @Override
    public ValidateResultDTO validate(QueryRequestDTO request) {
        String queryId = resolveQueryId(request);
        QueryGuardContext context = buildContext(request, false, queryId);
        SqlGuardDecision decision = sqlGuard.validate(context);
        if (!decision.isAllowed()) {
            List<String> codes = decision.getViolationCodes() == null ? List.of()
                    : decision.getViolationCodes().stream().map(GuardViolationCode::name).toList();
            return ValidateResultDTO.builder()
                    .queryId(queryId)
                    .valid(false)
                    .normalizedSql(decision.getNormalizedSql())
                    .sqlFingerprint(decision.getSqlFingerprint())
                    .violationCodes(codes)
                    .message(decision.getMessage())
                    .validatedAt(System.currentTimeMillis())
                    .build();
        }
        return ValidateResultDTO.builder()
                .queryId(queryId)
                .valid(true)
                .normalizedSql(decision.getNormalizedSql())
                .sqlFingerprint(decision.getSqlFingerprint())
                .violationCodes(List.of())
                .message(decision.getMessage())
                .validatedAt(System.currentTimeMillis())
                .build();
    }

    @Override
    public QueryResultDTO preview(QueryRequestDTO request) {
        GuardedRequest guardedRequest = guard(request, true, false);
        QueryCommand command = buildCommand(request, guardedRequest.decision(), guardedRequest.queryId(), true);
        log.info("Submitting preview query: queryId={}, tenantId={}, datasourceId={}, timeoutMs={}, pageSize={}, maxRows={}",
                guardedRequest.queryId(), guardedRequest.tenantId(), guardedRequest.datasource().getId(), command.getTimeoutMs(), command.getPageSize(), command.getMaxRows());
        try (QueryGovernanceLimiter.Lease ignored = queryGovernanceLimiter.acquire(
                guardedRequest.tenantId(),
                tenantQpsLimit,
                guardedRequest.datasource().getId(),
                datasourceConcurrencyLimit)) {
            queryMetricsService.updateDatasourceConcurrency(queryGovernanceLimiter.currentDatasourceConcurrency());
            String cacheKey = buildCacheKey(request, guardedRequest.decision());
            if (useCache(request)) {
                QueryResult cached = queryCacheStore.get(cacheKey).orElse(null);
                if (cached != null) {
                    log.info("Preview query hit cache: queryId={}, datasourceId={}, rowCount={}",
                            guardedRequest.queryId(), guardedRequest.datasource().getId(), cached.getRowCount());
                    QueryExecution execution = buildExecutionRecord(guardedRequest, MODE_PREVIEW, cached, true, ExecutionStatus.SUCCEEDED, null, null);
                    recordExecution(execution);
                    return toQueryResultDTO(guardedRequest.queryId(), guardedRequest.datasource(), cached, true, request.getOption(), MODE_PREVIEW, execution.getStartedAt(), execution.getFinishedAt(), ExecutionStatus.SUCCEEDED, null);
                }
            }
            try {
                Connector connector = connectorFactory.create(guardedRequest.datasource());
                QueryResult result = connector.execute(guardedRequest.datasource(), command);
                QueryResult finalResult = QueryResult.builder()
                        .fields(result.getFields())
                        .rows(result.getRows())
                        .rowCount(result.getRowCount())
                        .truncated(result.getTruncated())
                        .nextCursor(result.getNextCursor())
                        .elapsedMs(result.getElapsedMs())
                        .cached(false)
                        .build();
                if (useCache(request)) {
                    queryCacheStore.put(cacheKey, finalResult, resolveCacheTtlSeconds(request.getOption()));
                }
                QueryExecution execution = buildExecutionRecord(guardedRequest, MODE_PREVIEW, finalResult, false, ExecutionStatus.SUCCEEDED, null, null);
                recordExecution(execution);
                return toQueryResultDTO(guardedRequest.queryId(), guardedRequest.datasource(), finalResult, false, request.getOption(), MODE_PREVIEW, execution.getStartedAt(), execution.getFinishedAt(), ExecutionStatus.SUCCEEDED, null);
            } catch (IllegalStateException e) {
                BaseBusinessException exception = mapExecutionException(e);
                log.warn("Preview query failed: queryId={}, datasourceId={}, errorCode={}, message={}",
                        guardedRequest.queryId(), guardedRequest.datasource().getId(), exception.getErrorCode(), e.getMessage(), e);
                QueryExecution execution = buildExecutionFailure(guardedRequest, MODE_PREVIEW, exception.getErrorCode(), exception.getMessage());
                recordExecution(execution);
                if (exception.getErrorCode() == ErrorCode.QUERY_TIMEOUT) {
                    queryMetricsService.recordTimeout(guardedRequest.datasource().getId());
                }
                throw exception;
            }
        } finally {
            queryMetricsService.updateDatasourceConcurrency(queryGovernanceLimiter.currentDatasourceConcurrency());
        }
    }

    @Override
    public AsyncSubmitResponseDTO runAsync(QueryRequestDTO request) {
        GuardedRequest guardedRequest = guard(request, false, false);
        QueryCommand command = buildCommand(request, guardedRequest.decision(), guardedRequest.queryId(), false);
        log.info("Submitting async query: queryId={}, tenantId={}, datasourceId={}, timeoutMs={}, pageSize={}, maxRows={}",
                guardedRequest.queryId(), guardedRequest.tenantId(), guardedRequest.datasource().getId(), command.getTimeoutMs(), command.getPageSize(), command.getMaxRows());
        QueryGovernanceLimiter.Lease lease = queryGovernanceLimiter.acquire(
                guardedRequest.tenantId(),
                tenantQpsLimit,
                guardedRequest.datasource().getId(),
                datasourceConcurrencyLimit);
        queryMetricsService.updateDatasourceConcurrency(queryGovernanceLimiter.currentDatasourceConcurrency());
        long now = System.currentTimeMillis();
        QueryExecution execution = QueryExecution.builder()
                .queryId(guardedRequest.queryId())
                .tenantId(guardedRequest.tenantId())
                .datasourceId(guardedRequest.datasource().getId())
                .sqlFingerprint(guardedRequest.decision().getSqlFingerprint())
                .mode(MODE_RUN)
                .status(ExecutionStatus.QUEUED)
                .startedAt(now)
                .finishedAt(null)
                .elapsedMs(null)
                .cached(false)
                .truncated(false)
                .rowCount(0)
                .errorCode(null)
                .errorMessage(null)
                .operatorId(guardedRequest.operatorId())
                .createdAt(now)
                .build();
        String taskId = toTaskId(guardedRequest.queryId());
        AsyncTask task = AsyncTask.builder()
                .taskId(taskId)
                .taskType(TaskType.QUERY)
                .refId(guardedRequest.queryId())
                .tenantId(guardedRequest.tenantId())
                .operatorId(guardedRequest.operatorId())
                .status(ExecutionStatus.QUEUED)
                .createdAt(now)
                .updatedAt(now)
                .build();
        asyncTaskRepository.save(task);
        queryAuditLogService.logTask(task);
        asyncQueryExecutor.submit(execution, guardedRequest.datasource(), command, lease);
        return AsyncSubmitResponseDTO.builder()
                .taskId(taskId)
                .status(ExecutionStatus.QUEUED)
                .pollUrl("/api/v1/tasks/" + taskId)
                .build();
    }

    @Override
    public void cancel(String queryId) {
        QueryExecution execution = queryExecutionRepository.findById(queryId)
                .orElseThrow(() -> new BaseBusinessException(ErrorCode.EXECUTION_RECORD_NOT_FOUND, "query execution not found"));
        if (execution.getStatus() == ExecutionStatus.CANCELLED) {
            throw new BaseBusinessException(ErrorCode.QUERY_ALREADY_CANCELLED, "query already cancelled");
        }
        if (execution.getStatus() != ExecutionStatus.QUEUED && execution.getStatus() != ExecutionStatus.RUNNING) {
            throw new BaseBusinessException(ErrorCode.ASYNC_TASK_NOT_FOUND, "query not running");
        }
        if (!queryCancellationRegistry.cancel(queryId)) {
            log.warn("Query cancellation fallback to status update: queryId={}", queryId);
            queryExecutionRepository.updateStatus(queryId, ExecutionStatus.CANCELLED, System.currentTimeMillis(), ErrorCode.QUERY_CANCELLED.getCode(), "query cancellation requested before statement registration");
        }
    }

    @Override
    public QueryResultDTO getStatus(String queryId) {
        QueryExecution execution = queryExecutionRepository.findById(queryId)
                .orElseThrow(() -> new BaseBusinessException(ErrorCode.EXECUTION_RECORD_NOT_FOUND, "query execution not found"));
        return QueryResultDTO.builder()
                .queryId(execution.getQueryId())
                .status(execution.getStatus())
                .dataset(null)
                .executionMeta(QueryExecutionMetaDTO.builder()
                        .queryId(execution.getQueryId())
                        .mode(execution.getMode())
                        .startedAt(execution.getStartedAt())
                        .finishedAt(execution.getFinishedAt())
                        .elapsedMs(execution.getElapsedMs())
                        .cached(execution.getCached())
                        .truncated(execution.getTruncated())
                        .rowCount(execution.getRowCount())
                        .returnedRowCount(execution.getRowCount())
                        .engineType(null)
                        .build())
                .error(toErrorInfo(execution))
                .build();
    }

    @Override
    public SchemaInferResultDTO inferSchema(QueryRequestDTO request) {
        GuardedRequest guardedRequest = guard(request, true, false);
        return schemaInferService.infer(guardedRequest.datasource(), guardedRequest.decision().getNormalizedSql(), guardedRequest.queryId());
    }

    private GuardedRequest guard(QueryRequestDTO request, boolean preview, boolean tolerateValidationFailure) {
        String queryId = resolveQueryId(request);
        QueryGuardContext context = buildContext(request, preview, queryId);
        SqlGuardDecision decision = sqlGuard.validate(context);
        RequestContextDTO requestContext = request == null ? null : request.getContext();
        String tenantId = requestContext == null ? null : requestContext.getTenantId();
        AnalysisDatasource datasource = datasourceRepository.findByIdAndTenantId(request == null ? null : request.getDatasourceId(), tenantId)
                .orElseThrow(() -> new BaseBusinessException(ErrorCode.DATASOURCE_NOT_FOUND, "datasource not found"));
        if (datasource.getStatus() != null && datasource.getStatus() != DatasourceStatus.ACTIVE) {
            throw new BaseBusinessException(ErrorCode.DATASOURCE_CONNECTION_FAILED, "datasource is not active");
        }
        if (!decision.isAllowed() && !tolerateValidationFailure) {
            throw new SqlGuardRejectedException(mapErrorCode(decision.getViolationCodes()), decision.getMessage(), decision.getViolationCodes());
        }
        return new GuardedRequest(queryId, tenantId, requestContext == null ? null : requestContext.getUserId(), datasource, decision);
    }

    private QueryCommand buildCommand(QueryRequestDTO request, SqlGuardDecision decision, String queryId, boolean preview) {
        QueryOptionDTO option = request == null ? null : request.getOption();
        QueryGovernancePolicy policy = QueryGovernancePolicy.defaultPolicy();
        int pageSize = resolvePageSize(option);
        return QueryCommand.builder()
                .queryId(queryId)
                .normalizedSql(decision.getNormalizedSql())
                .parameters(request == null || request.getParameters() == null ? Map.of() : request.getParameters())
                .timeoutMs(resolveTimeoutMs(option, policy, preview))
                .maxRows(resolveMaxRows(option, policy, preview, pageSize))
                .paginationMode(resolvePaginationMode(option))
                .offset(option == null ? 0 : option.getOffset())
                .pageSize(pageSize)
                .cursor(option == null ? null : option.getCursor())
                .build();
    }

    private QueryExecution buildExecutionRecord(GuardedRequest guardedRequest,
                                                String mode,
                                                QueryResult result,
                                                boolean cached,
                                                ExecutionStatus status,
                                                String errorCode,
                                                String errorMessage) {
        long finishedAt = System.currentTimeMillis();
        long elapsedMs = result.getElapsedMs() == null ? 0L : result.getElapsedMs();
        return QueryExecution.builder()
                .queryId(guardedRequest.queryId())
                .tenantId(guardedRequest.tenantId())
                .datasourceId(guardedRequest.datasource().getId())
                .sqlFingerprint(guardedRequest.decision().getSqlFingerprint())
                .mode(mode)
                .status(status)
                .startedAt(finishedAt - elapsedMs)
                .finishedAt(finishedAt)
                .elapsedMs(elapsedMs)
                .cached(cached)
                .truncated(Boolean.TRUE.equals(result.getTruncated()))
                .rowCount(result.getRowCount() == null ? 0 : result.getRowCount())
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .operatorId(guardedRequest.operatorId())
                .createdAt(finishedAt - elapsedMs)
                .build();
    }

    private QueryExecution buildExecutionFailure(GuardedRequest guardedRequest, String mode, ErrorCode errorCode, String message) {
        long now = System.currentTimeMillis();
        return QueryExecution.builder()
                .queryId(guardedRequest.queryId())
                .tenantId(guardedRequest.tenantId())
                .datasourceId(guardedRequest.datasource().getId())
                .sqlFingerprint(guardedRequest.decision().getSqlFingerprint())
                .mode(mode)
                .status(errorCode == ErrorCode.QUERY_CANCELLED ? ExecutionStatus.CANCELLED : ExecutionStatus.FAILED)
                .startedAt(now)
                .finishedAt(now)
                .elapsedMs(0L)
                .cached(false)
                .truncated(false)
                .rowCount(0)
                .errorCode(errorCode.getCode())
                .errorMessage(message)
                .operatorId(guardedRequest.operatorId())
                .createdAt(now)
                .build();
    }

    private void recordExecution(QueryExecution execution) {
        boolean slowQuery = execution.getElapsedMs() != null && execution.getElapsedMs() >= slowQueryThresholdMs;
        queryAuditLogService.logQuery(execution, slowQuery);
        queryMetricsService.recordQuery(execution.getStatus(), execution.getElapsedMs(), slowQuery, execution.getDatasourceId());
    }

    private QueryResultDTO toQueryResultDTO(String queryId, AnalysisDatasource datasource, QueryResult result, boolean cached,
                                            QueryOptionDTO option, String mode, Long startedAt, Long finishedAt,
                                            ExecutionStatus status, ErrorInfoDTO errorInfo) {
        List<Map<String, Object>> rows = result.getRows() == null ? List.of() : result.getRows();
        int pageSize = resolvePageSize(option);
        int offset = option == null || option.getOffset() == null ? 0 : option.getOffset();
        DatasetDTO dataset = DatasetDTO.builder()
                .schema(DatasetSchemaDTO.builder()
                        .fields(result.getFields())
                        .metrics(List.of())
                        .dimensions(List.of())
                        .timeFields(List.of())
                        .build())
                .rows(rows)
                .page(DatasetPageDTO.builder()
                        .pageSize(pageSize)
                        .currentPage(pageSize <= 0 ? 1 : offset / pageSize + 1)
                        .total((long) rows.size())
                        .nextCursor(result.getNextCursor())
                        .build())
                .stat(DatasetStatDTO.builder()
                        .rowCount(result.getRowCount())
                        .returnedRowCount(rows.size())
                        .truncated(Boolean.TRUE.equals(result.getTruncated()))
                        .build())
                .build();
        return QueryResultDTO.builder()
                .queryId(queryId)
                .status(status)
                .dataset(dataset)
                .executionMeta(QueryExecutionMetaDTO.builder()
                        .queryId(queryId)
                        .mode(mode)
                        .startedAt(startedAt)
                        .finishedAt(finishedAt)
                        .elapsedMs(result.getElapsedMs())
                        .cached(cached)
                        .truncated(result.getTruncated())
                        .rowCount(result.getRowCount())
                        .returnedRowCount(rows.size())
                        .engineType(datasource.getType().name().toLowerCase())
                        .build())
                .error(errorInfo)
                .build();
    }

    private ErrorInfoDTO toErrorInfo(QueryExecution execution) {
        if (execution.getErrorCode() == null) {
            return null;
        }
        return ErrorInfoDTO.builder()
                .code(execution.getErrorCode())
                .message(execution.getErrorMessage())
                .detail(execution.getErrorMessage())
                .requestId(execution.getQueryId())
                .retryable(false)
                .build();
    }

    private boolean useCache(QueryRequestDTO request) {
        return request != null && request.getOption() != null && Boolean.TRUE.equals(request.getOption().getUseCache());
    }

    private int resolveCacheTtlSeconds(QueryOptionDTO option) {
        if (option != null && option.getCacheTtlSeconds() != null && option.getCacheTtlSeconds() > 0) {
            return option.getCacheTtlSeconds();
        }
        return defaultCacheTtlSeconds;
    }

    private String buildCacheKey(QueryRequestDTO request, SqlGuardDecision decision) {
        RequestContextDTO context = request == null ? null : request.getContext();
        return QueryCacheKeyBuilder.build(context == null ? null : context.getTenantId(), request == null ? null : request.getDatasourceId(), decision.getNormalizedSql());
    }

    private int resolveTimeoutMs(QueryOptionDTO option, QueryGovernancePolicy policy, boolean preview) {
        if (option != null && option.getTimeoutMs() != null && option.getTimeoutMs() > 0) {
            return Math.min(option.getTimeoutMs(), policy.getMaxTimeoutMs());
        }
        return preview ? defaultTimeoutMs : policy.getDefaultRunTimeoutMs();
    }

    private int resolveMaxRows(QueryOptionDTO option, QueryGovernancePolicy policy, boolean preview, int pageSize) {
        int policyMaxRows = preview ? policy.getPreviewMaxRows() : policy.getRunMaxRows();
        int requestedLimit = option != null && option.getLimit() != null && option.getLimit() > 0 ? option.getLimit() : pageSize;
        return Math.min(requestedLimit, policyMaxRows);
    }

    private int resolvePageSize(QueryOptionDTO option) {
        if (option != null && option.getPageSize() != null && option.getPageSize() > 0) {
            return option.getPageSize();
        }
        if (option != null && option.getLimit() != null && option.getLimit() > 0) {
            return Math.min(option.getLimit(), defaultPageSize);
        }
        return defaultPageSize;
    }

    private PaginationMode resolvePaginationMode(QueryOptionDTO option) {
        if (option != null && option.getCursor() != null && !option.getCursor().isBlank()) {
            return PaginationMode.CURSOR;
        }
        return PaginationMode.OFFSET;
    }

    private QueryGuardContext buildContext(QueryRequestDTO request, boolean preview, String queryId) {
        if (request == null) {
            return QueryGuardContext.builder()
                    .queryId(queryId)
                    .preview(preview)
                    .policy(QueryGovernancePolicy.defaultPolicy())
                    .build();
        }
        QueryOptionDTO option = request.getOption();
        return QueryGuardContext.builder()
                .queryId(queryId)
                .tenantId(request.getContext() == null ? null : request.getContext().getTenantId())
                .operatorId(request.getContext() == null ? null : request.getContext().getUserId())
                .datasourceId(request.getDatasourceId())
                .sql(request.getSql())
                .requestedLimit(option == null ? null : option.getLimit())
                .timeoutMs(option == null ? null : option.getTimeoutMs())
                .preview(preview)
                .policy(QueryGovernancePolicy.defaultPolicy())
                .build();
    }

    private String resolveQueryId(QueryRequestDTO request) {
        if (request != null && request.getRequestId() != null && !request.getRequestId().isBlank()) {
            return request.getRequestId();
        }
        return "q_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String toTaskId(String queryId) {
        return "task-" + queryId;
    }

    private BaseBusinessException mapExecutionException(IllegalStateException e) {
        if (e.getCause() instanceof SQLTimeoutException) {
            return new BaseBusinessException(ErrorCode.QUERY_TIMEOUT, "query timeout", e.getMessage(), null, false);
        }
        if (e.getMessage() != null && e.getMessage().toLowerCase().contains("cancel")) {
            return new BaseBusinessException(ErrorCode.QUERY_CANCELLED, "query cancelled", e.getMessage(), null, false);
        }
        return new BaseBusinessException(ErrorCode.DOWNSTREAM_ERROR, "query execution failed", e.getMessage(), null, false);
    }

    private ErrorCode mapErrorCode(List<GuardViolationCode> violations) {
        if (violations == null || violations.isEmpty()) {
            return ErrorCode.SQL_SECURITY_REJECTED;
        }
        if (violations.contains(GuardViolationCode.SQL_PARSE_FAILED)) {
            return ErrorCode.SQL_PARSE_FAILED;
        }
        if (violations.contains(GuardViolationCode.SQL_MULTI_STATEMENT_REJECTED)) {
            return ErrorCode.SQL_MULTI_STATEMENT_REJECTED;
        }
        if (violations.contains(GuardViolationCode.SQL_FORBIDDEN_STATEMENT)) {
            return ErrorCode.SQL_FORBIDDEN_STATEMENT;
        }
        if (violations.contains(GuardViolationCode.SQL_LOCK_CLAUSE_FORBIDDEN)) {
            return ErrorCode.SQL_LOCK_CLAUSE_FORBIDDEN;
        }
        if (violations.contains(GuardViolationCode.SQL_NOT_READONLY)) {
            return ErrorCode.SQL_NOT_READONLY;
        }
        if (violations.contains(GuardViolationCode.QUERY_LIMIT_EXCEEDED)) {
            return ErrorCode.QUERY_LIMIT_EXCEEDED;
        }
        return ErrorCode.QUERY_VALIDATION_FAILED;
    }

    private record GuardedRequest(String queryId, String tenantId, String operatorId, AnalysisDatasource datasource,
                                  SqlGuardDecision decision) {
    }
}
