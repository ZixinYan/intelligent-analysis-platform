package com.kuaishou.intelligentanalysisplatform.infra.repository;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.domain.query.execution.QueryExecution;
import com.kuaishou.intelligentanalysisplatform.domain.query.execution.QueryExecutionRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcQueryExecutionRepository implements QueryExecutionRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcQueryExecutionRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(QueryExecution execution) {
        jdbcTemplate.update("""
                INSERT INTO query_execution (
                    query_id, tenant_id, datasource_id, sql_fingerprint, mode, status,
                    started_at, finished_at, elapsed_ms, cached, truncated, row_count,
                    error_code, error_message, operator_id, created_at
                ) VALUES (
                    :queryId, :tenantId, :datasourceId, :sqlFingerprint, :mode, :status,
                    :startedAt, :finishedAt, :elapsedMs, :cached, :truncated, :rowCount,
                    :errorCode, :errorMessage, :operatorId, :createdAt
                )
                """, toParams(execution));
    }

    @Override
    public void updateStatus(String queryId, ExecutionStatus status, Long finishedAt, String errorCode, String errorMessage) {
        jdbcTemplate.update("""
                UPDATE query_execution
                SET status = :status,
                    finished_at = :finishedAt,
                    error_code = :errorCode,
                    error_message = :errorMessage
                WHERE query_id = :queryId
                """, new MapSqlParameterSource()
                .addValue("queryId", queryId)
                .addValue("status", status == null ? null : status.name())
                .addValue("finishedAt", finishedAt)
                .addValue("errorCode", errorCode)
                .addValue("errorMessage", errorMessage));
    }

    @Override
    public void updateResult(String queryId, ExecutionStatus status, Long finishedAt, Long elapsedMs,
                             boolean cached, boolean truncated, int rowCount) {
        jdbcTemplate.update("""
                UPDATE query_execution
                SET status = :status,
                    finished_at = :finishedAt,
                    elapsed_ms = :elapsedMs,
                    cached = :cached,
                    truncated = :truncated,
                    row_count = :rowCount,
                    error_code = NULL,
                    error_message = NULL
                WHERE query_id = :queryId
                """, new MapSqlParameterSource()
                .addValue("queryId", queryId)
                .addValue("status", status == null ? null : status.name())
                .addValue("finishedAt", finishedAt)
                .addValue("elapsedMs", elapsedMs)
                .addValue("cached", cached)
                .addValue("truncated", truncated)
                .addValue("rowCount", rowCount));
    }

    @Override
    public Optional<QueryExecution> findById(String queryId) {
        return jdbcTemplate.query("""
                SELECT query_id, tenant_id, datasource_id, sql_fingerprint, mode, status,
                       started_at, finished_at, elapsed_ms, cached, truncated, row_count,
                       error_code, error_message, operator_id, created_at
                FROM query_execution
                WHERE query_id = :queryId
                """, Map.of("queryId", queryId), rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(QueryExecution.builder()
                    .queryId(rs.getString("query_id"))
                    .tenantId(rs.getString("tenant_id"))
                    .datasourceId(rs.getString("datasource_id"))
                    .sqlFingerprint(rs.getString("sql_fingerprint"))
                    .mode(rs.getString("mode"))
                    .status(ExecutionStatus.valueOf(rs.getString("status")))
                    .startedAt(rs.getObject("started_at", Long.class))
                    .finishedAt(rs.getObject("finished_at", Long.class))
                    .elapsedMs(rs.getObject("elapsed_ms", Long.class))
                    .cached(rs.getObject("cached", Boolean.class))
                    .truncated(rs.getObject("truncated", Boolean.class))
                    .rowCount(rs.getObject("row_count", Integer.class))
                    .errorCode(rs.getString("error_code"))
                    .errorMessage(rs.getString("error_message"))
                    .operatorId(rs.getString("operator_id"))
                    .createdAt(rs.getLong("created_at"))
                    .build());
        });
    }

    private MapSqlParameterSource toParams(QueryExecution execution) {
        return new MapSqlParameterSource()
                .addValue("queryId", execution.getQueryId())
                .addValue("tenantId", execution.getTenantId())
                .addValue("datasourceId", execution.getDatasourceId())
                .addValue("sqlFingerprint", execution.getSqlFingerprint())
                .addValue("mode", execution.getMode())
                .addValue("status", execution.getStatus() == null ? null : execution.getStatus().name())
                .addValue("startedAt", execution.getStartedAt())
                .addValue("finishedAt", execution.getFinishedAt())
                .addValue("elapsedMs", execution.getElapsedMs())
                .addValue("cached", execution.getCached())
                .addValue("truncated", execution.getTruncated())
                .addValue("rowCount", execution.getRowCount())
                .addValue("errorCode", execution.getErrorCode())
                .addValue("errorMessage", execution.getErrorMessage())
                .addValue("operatorId", execution.getOperatorId())
                .addValue("createdAt", execution.getCreatedAt());
    }
}
