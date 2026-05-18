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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 查询应用服务的默认实现。
 *
 * <p>职责链：
 * <ol>
 *   <li><b>SQL 安全卫兵（guard）</b>：通过 {@link SqlGuard} 对 SQL 进行解析、标准化和安全规则校验，
 *       阻断多语句、写操作、危险子句等风险 SQL。</li>
 *   <li><b>流量治理</b>：通过 {@link QueryGovernanceLimiter} 在租户 QPS 和数据源并发两个维度限流，
 *       保护下游数据库不被打爆。</li>
 *   <li><b>缓存命中</b>：预览模式下按 tenantId + datasourceId + 标准化 SQL 构建缓存键，
 *       命中则直接返回，不消耗数据库连接。</li>
 *   <li><b>连接器执行</b>：通过 {@link ConnectorFactory} 获取对应数据库方言的 Connector，
 *       执行分页查询并截断超限行。</li>
 *   <li><b>可观测性</b>：执行后写入审计日志 & 指标（慢查询判定、超时计数、并发量）。</li>
 * </ol>
 *
 * <p>支持三种执行模式：
 * <ul>
 *   <li>{@code PREVIEW}（{@link #preview}）：同步执行，返回有限行数，超时短，适合实时预览。</li>
 *   <li>{@code RUN_ASYNC}（{@link #runAsync}）：异步提交，返回 taskId 供轮询，适合大数据量全量查询。</li>
 *   <li>{@code VALIDATE}（{@link #validate}）：只做 SQL 合法性校验，不实际执行查询。</li>
 * </ul>
 */
@Service
public class DefaultQueryApplicationService implements QueryApplicationService {
    /** 预览模式标识，写入执行记录的 mode 字段 */
    private static final String MODE_PREVIEW = "PREVIEW";
    /** 全量运行模式标识 */
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

    /**
     * SQL 合法性预校验。仅通过 SqlGuard 验证 SQL 语法和安全规则，不执行实际查询。
     *
     * @param request 查询请求，包含 datasourceId、sql 及可选的 requestId
     * @return 校验结果，包含标准化 SQL、SQL 指纹及违规码列表
     */
    @Override
    public ValidateResultDTO validate(QueryRequestDTO request) {
        GuardedRequest guardedRequest = guard(request, false, false);
        return ValidateResultDTO.builder()
                .queryId(guardedRequest.queryId())
                .valid(true)
                .normalizedSql(guardedRequest.decision().getNormalizedSql())
                .sqlFingerprint(guardedRequest.decision().getSqlFingerprint())
                .violationCodes(List.of())
                .message(guardedRequest.decision().getMessage())
                .validatedAt(System.currentTimeMillis())
                .build();
    }

    /**
     * 同步预览查询。适合用户在编辑 SQL 时实时预览结果，超时时间短（默认 10s）。
     *
     * <p>执行流程：
     * <ol>
     *   <li>guard：SQL 安全卫兵校验 + 数据源有效性检查</li>
     *   <li>acquire：获取治理令牌（租户 QPS / 数据源并发双重限流）</li>
     *   <li>cache：检查查询结果缓存，命中则直接返回</li>
     *   <li>execute：通过 Connector 执行分页 SQL，写入缓存</li>
     *   <li>record：记录执行日志 + 指标（慢查询、错误率）</li>
     * </ol>
     *
     * @param request 查询请求（option.useCache=true 时启用缓存）
     * @return 含数据集、执行元数据的查询结果
     * @throws BaseBusinessException SQL 校验失败、数据源不可用或超时时抛出
     */
    @Override
    public QueryResultDTO preview(QueryRequestDTO request) {
        GuardedRequest guardedRequest = guard(request, true, false);
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
                    QueryExecution execution = buildExecutionRecord(guardedRequest, MODE_PREVIEW, cached, true, ExecutionStatus.SUCCEEDED, null, null);
                    recordExecution(execution);
                    return toQueryResultDTO(guardedRequest.queryId(), guardedRequest.datasource(), cached, true, request.getOption(), MODE_PREVIEW, execution.getStartedAt(), execution.getFinishedAt(), ExecutionStatus.SUCCEEDED, null);
                }
            }
            try {
                Connector connector = connectorFactory.create(guardedRequest.datasource());
                QueryResult result = connector.execute(guardedRequest.datasource(), buildCommand(request, guardedRequest.decision(), guardedRequest.queryId(), true));
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

    /**
     * 异步提交全量查询任务。适合大数据量、长时查询场景。
     *
     * <p>设计要点：
     * <ul>
     *   <li>限流令牌在提交时 acquire，任务完成后由 {@link AsyncQueryExecutor} 释放，
     *       保证下游数据库并发在全生命周期内受控。</li>
     *   <li>queryId 和 taskId 通过 {@link #toTaskId} 建立关联，前端通过 taskId 轮询状态。</li>
     *   <li>执行状态初始为 {@code QUEUED}，由异步线程更新为 RUNNING / SUCCEEDED / FAILED。</li>
     * </ul>
     *
     * @param request 查询请求
     * @return 包含 taskId 和初始状态 QUEUED 的提交响应
     */
    @Override
    public AsyncSubmitResponseDTO runAsync(QueryRequestDTO request) {
        GuardedRequest guardedRequest = guard(request, false, false);
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
        asyncQueryExecutor.submit(execution, guardedRequest.datasource(), buildCommand(request, guardedRequest.decision(), guardedRequest.queryId(), false), lease);
        return AsyncSubmitResponseDTO.builder()
                .taskId(taskId)
                .status(ExecutionStatus.QUEUED)
                .pollUrl("/api/v1/tasks/" + taskId)
                .build();
    }

    /**
     * 取消正在运行的查询。
     *
     * <p>取消机制：
     * <ul>
     *   <li>通过 {@link QueryCancellationRegistry} 调用底层 JDBC Statement.cancel()。</li>
     *   <li>若 Statement 尚未注册（任务刚提交还未到执行阶段），直接将状态置为 CANCELLED。</li>
     * </ul>
     *
     * @param queryId 要取消的查询 ID
     * @throws BaseBusinessException 查询不存在、已取消或非运行态时抛出
     */
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

    /**
     * 查询前置卫兵：SQL 校验 + 数据源校验。
     *
     * @param request                 查询请求
     * @param preview                 是否预览模式（影响 SQL 校验策略）
     * @param tolerateValidationFailure 是否允许 SQL 校验失败继续（用于仅语法检查场景）
     * @return 封装了 queryId、tenantId、数据源、卫兵决策的不可变记录
     * @throws BaseBusinessException 数据源不存在、状态异常或 SQL 被拦截时抛出
     */
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

    /**
     * 根据请求参数和卫兵决策构建 {@link QueryCommand}。
     *
     * <p>分页策略（pageSize / offset / cursor）和超时时间在此统一解析，
     * 由治理策略上限和请求参数取最小值，防止请求方绕过治理阈值。
     */
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

    /**
     * 将执行结果写入审计日志并更新查询指标。
     *
     * <p>慢查询判定：elapsedMs &ge; slowQueryThresholdMs（默认 5000ms）时标记为慢查询，
     * 同时触发 metrics 计数器，便于告警和排查。
     */
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

    /**
     * 将 JDBC 层抛出的 {@link IllegalStateException} 映射为业务异常。
     *
     * <p>JDBC 驱动在超时、取消时均以 IllegalStateException 包装，
     * 通过 cause 类型和错误信息关键词进行区分，映射为对应错误码。
     */
    private BaseBusinessException mapExecutionException(IllegalStateException e) {
        if (e.getCause() instanceof SQLTimeoutException) {
            return new BaseBusinessException(ErrorCode.QUERY_TIMEOUT, "query timeout", e.getMessage(), null, false);
        }
        if (e.getMessage() != null && e.getMessage().toLowerCase().contains("cancel")) {
            return new BaseBusinessException(ErrorCode.QUERY_CANCELLED, "query cancelled", e.getMessage(), null, false);
        }
        return new BaseBusinessException(ErrorCode.DOWNSTREAM_ERROR, "query execution failed", e.getMessage(), null, false);
    }

    /**
     * 将 SQL 卫兵违规码列表映射为精确的业务错误码，方便前端展示具体原因。
     * 优先级：解析失败 > 多语句 > 禁止语句 > 加锁子句 > 非只读 > 行数超限 > 通用校验失败。
     */
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

    /**
     * 卫兵检查通过后的不可变上下文，贯穿整个查询生命周期（preview / runAsync / cancel）。
     *
     * @param queryId    本次查询唯一 ID（来自请求或随机生成）
     * @param tenantId   租户 ID，用于限流隔离和缓存键
     * @param operatorId 操作人 ID，写入审计日志
     * @param datasource 已验证的数据源实体
     * @param decision   SQL 卫兵的校验决策（含标准化 SQL 和指纹）
     */
    private record GuardedRequest(String queryId, String tenantId, String operatorId, AnalysisDatasource datasource,
                                  SqlGuardDecision decision) {
    }
}
