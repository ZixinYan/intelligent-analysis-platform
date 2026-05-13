package com.kuaishou.intelligentanalysisplatform.infra.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.kuaishou.intelligentanalysisplatform.domain.workflow.WorkflowRunLog;
import com.kuaishou.intelligentanalysisplatform.domain.workflow.WorkflowRunLogRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkflowRunLogRepository implements WorkflowRunLogRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcWorkflowRunLogRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(WorkflowRunLog runLog) {
        jdbcTemplate.update("""
                INSERT INTO workflow_run_log (
                    run_id, workflow_id, version_id, tenant_id, trigger_type,
                    status, node_count, started_at, finished_at, elapsed_ms,
                    node_trace_json, created_by
                ) VALUES (
                    :runId, :workflowId, :versionId, :tenantId, :triggerType,
                    :status, :nodeCount, :startedAt, :finishedAt, :elapsedMs,
                    :nodeTraceJson, :createdBy
                )
                """, toParams(runLog));
    }

    @Override
    public void complete(String runId, String status, long elapsedMs, Long finishedAt, String nodeTraceJson) {
        jdbcTemplate.update("""
                UPDATE workflow_run_log
                SET status = :status,
                    elapsed_ms = :elapsedMs,
                    finished_at = :finishedAt,
                    node_trace_json = :nodeTraceJson
                WHERE run_id = :runId
                """, new MapSqlParameterSource()
                .addValue("runId", runId)
                .addValue("status", status)
                .addValue("elapsedMs", elapsedMs)
                .addValue("finishedAt", finishedAt)
                .addValue("nodeTraceJson", nodeTraceJson));
    }

    @Override
    public Optional<WorkflowRunLog> findByRunId(String runId) {
        return jdbcTemplate.query("""
                SELECT run_id, workflow_id, version_id, tenant_id, trigger_type,
                       status, node_count, started_at, finished_at, elapsed_ms,
                       node_trace_json, created_by
                FROM workflow_run_log
                WHERE run_id = :runId
                """, Map.of("runId", runId), rs -> {
            if (!rs.next()) return Optional.empty();
            return Optional.of(mapRow(rs));
        });
    }

    @Override
    public List<WorkflowRunLog> findByWorkflowId(String workflowId, String status, int offset, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("workflowId", workflowId)
                .addValue("offset", offset)
                .addValue("limit", limit);

        String sql;
        if (status != null && !status.isBlank()) {
            params.addValue("status", status);
            sql = """
                    SELECT run_id, workflow_id, version_id, tenant_id, trigger_type,
                           status, node_count, started_at, finished_at, elapsed_ms,
                           node_trace_json, created_by
                    FROM workflow_run_log
                    WHERE workflow_id = :workflowId AND status = :status
                    ORDER BY started_at DESC
                    LIMIT :limit OFFSET :offset
                    """;
        } else {
            sql = """
                    SELECT run_id, workflow_id, version_id, tenant_id, trigger_type,
                           status, node_count, started_at, finished_at, elapsed_ms,
                           node_trace_json, created_by
                    FROM workflow_run_log
                    WHERE workflow_id = :workflowId
                    ORDER BY started_at DESC
                    LIMIT :limit OFFSET :offset
                    """;
        }
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> mapRow(rs));
    }

    @Override
    public long countByWorkflowId(String workflowId, String status) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("workflowId", workflowId);
        String sql;
        if (status != null && !status.isBlank()) {
            params.addValue("status", status);
            sql = "SELECT COUNT(1) FROM workflow_run_log WHERE workflow_id = :workflowId AND status = :status";
        } else {
            sql = "SELECT COUNT(1) FROM workflow_run_log WHERE workflow_id = :workflowId";
        }
        Long count = jdbcTemplate.queryForObject(sql, params, Long.class);
        return count == null ? 0L : count;
    }

    private WorkflowRunLog mapRow(ResultSet rs) throws SQLException {
        return WorkflowRunLog.builder()
                .runId(rs.getString("run_id"))
                .workflowId(rs.getString("workflow_id"))
                .versionId(rs.getString("version_id"))
                .tenantId(rs.getString("tenant_id"))
                .triggerType(rs.getString("trigger_type"))
                .status(rs.getString("status"))
                .nodeCount(rs.getObject("node_count") != null ? rs.getInt("node_count") : null)
                .startedAt(rs.getLong("started_at"))
                .finishedAt(rs.getObject("finished_at") != null ? rs.getLong("finished_at") : null)
                .elapsedMs(rs.getObject("elapsed_ms") != null ? rs.getLong("elapsed_ms") : null)
                .nodeTraceJson(rs.getString("node_trace_json"))
                .createdBy(rs.getString("created_by"))
                .build();
    }

    private MapSqlParameterSource toParams(WorkflowRunLog r) {
        return new MapSqlParameterSource()
                .addValue("runId", r.getRunId())
                .addValue("workflowId", r.getWorkflowId())
                .addValue("versionId", r.getVersionId())
                .addValue("tenantId", r.getTenantId())
                .addValue("triggerType", r.getTriggerType())
                .addValue("status", r.getStatus())
                .addValue("nodeCount", r.getNodeCount())
                .addValue("startedAt", r.getStartedAt())
                .addValue("finishedAt", r.getFinishedAt())
                .addValue("elapsedMs", r.getElapsedMs())
                .addValue("nodeTraceJson", r.getNodeTraceJson())
                .addValue("createdBy", r.getCreatedBy());
    }
}
