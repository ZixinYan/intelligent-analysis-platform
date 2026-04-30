package com.kuaishou.intelligentanalysisplatform.infra.stub;

import com.kuaishou.intelligentanalysisplatform.application.QueryApplicationService;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.schema.AsyncSubmitResponseDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryExecutionMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryOptionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SchemaInferResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ValidateResultDTO;
import com.kuaishou.intelligentanalysisplatform.domain.query.SqlGuardRejectedException;
import com.kuaishou.intelligentanalysisplatform.domain.query.model.GuardViolationCode;
import com.kuaishou.intelligentanalysisplatform.domain.query.model.QueryGovernancePolicy;
import com.kuaishou.intelligentanalysisplatform.domain.query.model.QueryGuardContext;
import com.kuaishou.intelligentanalysisplatform.domain.query.model.SqlGuardDecision;
import com.kuaishou.intelligentanalysisplatform.domain.query.service.SqlGuard;

import java.util.List;

public class StubQueryApplicationService implements QueryApplicationService {

    private final SqlGuard sqlGuard;

    public StubQueryApplicationService(SqlGuard sqlGuard) {
        this.sqlGuard = sqlGuard;
    }

    @Override
    public ValidateResultDTO validate(QueryRequestDTO request) {
        SqlGuardDecision decision = validateDecision(request, false);
        return ValidateResultDTO.builder()
                .queryId(resolveQueryId(request))
                .valid(true)
                .normalizedSql(decision.getNormalizedSql())
                .sqlFingerprint(decision.getSqlFingerprint())
                .violationCodes(List.of())
                .message(decision.getMessage())
                .validatedAt(1L)
                .build();
    }

    @Override
    public QueryResultDTO preview(QueryRequestDTO request) {
        validateDecision(request, true);
        return QueryResultDTO.builder()
                .queryId(resolveQueryId(request))
                .status(ExecutionStatus.SUCCEEDED)
                .dataset(null)
                .executionMeta(QueryExecutionMetaDTO.builder()
                        .queryId(resolveQueryId(request))
                        .mode("PREVIEW")
                        .startedAt(1L)
                        .finishedAt(2L)
                        .elapsedMs(1L)
                        .cached(false)
                        .truncated(false)
                        .rowCount(0)
                        .returnedRowCount(0)
                        .engineType("stub")
                        .build())
                .error(null)
                .build();
    }

    @Override
    public AsyncSubmitResponseDTO runAsync(QueryRequestDTO request) {
        validateDecision(request, false);
        String queryId = resolveQueryId(request);
        return AsyncSubmitResponseDTO.builder()
                .taskId("task-" + queryId)
                .status(ExecutionStatus.QUEUED)
                .pollUrl("/api/v1/query/" + queryId + "/status")
                .build();
    }

    @Override
    public void cancel(String queryId) {
    }

    @Override
    public QueryResultDTO getStatus(String queryId) {
        return QueryResultDTO.builder()
                .queryId(queryId)
                .status(ExecutionStatus.RUNNING)
                .dataset(null)
                .executionMeta(QueryExecutionMetaDTO.builder()
                        .queryId(queryId)
                        .mode("RUN")
                        .startedAt(1L)
                        .finishedAt(null)
                        .elapsedMs(null)
                        .cached(false)
                        .truncated(false)
                        .rowCount(0)
                        .returnedRowCount(0)
                        .engineType("stub")
                        .build())
                .error(null)
                .build();
    }

    @Override
    public SchemaInferResultDTO inferSchema(QueryRequestDTO request) {
        throw new UnsupportedOperationException("stub");
    }

    private SqlGuardDecision validateDecision(QueryRequestDTO request, boolean preview) {
        QueryGuardContext context = buildContext(request, preview);
        SqlGuardDecision decision = sqlGuard.validate(context);
        if (!decision.isAllowed()) {
            throw new SqlGuardRejectedException(mapErrorCode(decision.getViolationCodes()), decision.getMessage(), decision.getViolationCodes());
        }
        return decision;
    }

    private QueryGuardContext buildContext(QueryRequestDTO request, boolean preview) {
        if (request == null) {
            return QueryGuardContext.builder()
                    .queryId("generated-query-id")
                    .preview(preview)
                    .policy(QueryGovernancePolicy.defaultPolicy())
                    .build();
        }
        QueryOptionDTO option = request.getOption();
        return QueryGuardContext.builder()
                .queryId(resolveQueryId(request))
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
        if (request == null || request.getRequestId() == null || request.getRequestId().isBlank()) {
            return "generated-query-id";
        }
        return request.getRequestId();
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
}
