package com.kuaishou.intelligentanalysisplatform.infra.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.kuaishou.intelligentanalysisplatform.domain.workflow.WorkflowVersion;
import com.kuaishou.intelligentanalysisplatform.domain.workflow.WorkflowVersionRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkflowVersionRepository implements WorkflowVersionRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcWorkflowVersionRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(WorkflowVersion version) {
        jdbcTemplate.update("""
                INSERT INTO workflow_version (
                    version_id, workflow_id, tenant_id, version_number,
                    definition_json, change_summary, published, created_by, created_at
                ) VALUES (
                    :versionId, :workflowId, :tenantId, :versionNumber,
                    :definitionJson, :changeSummary, :published, :createdBy, :createdAt
                )
                """, toParams(version));
    }

    @Override
    public Optional<WorkflowVersion> findByWorkflowIdAndVersionNumber(String workflowId, int versionNumber) {
        return jdbcTemplate.query("""
                SELECT version_id, workflow_id, tenant_id, version_number,
                       definition_json, change_summary, published, created_by, created_at
                FROM workflow_version
                WHERE workflow_id = :workflowId
                  AND version_number = :versionNumber
                """, Map.of("workflowId", workflowId, "versionNumber", versionNumber), rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(mapRow(rs));
        });
    }

    @Override
    public Optional<WorkflowVersion> findLatestByWorkflowId(String workflowId) {
        return jdbcTemplate.query("""
                SELECT version_id, workflow_id, tenant_id, version_number,
                       definition_json, change_summary, published, created_by, created_at
                FROM workflow_version
                WHERE workflow_id = :workflowId
                ORDER BY version_number DESC
                LIMIT 1
                """, Map.of("workflowId", workflowId), rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(mapRow(rs));
        });
    }

    @Override
    public List<WorkflowVersion> findByWorkflowId(String workflowId, int offset, int limit) {
        return jdbcTemplate.query("""
                SELECT version_id, workflow_id, tenant_id, version_number,
                       definition_json, change_summary, published, created_by, created_at
                FROM workflow_version
                WHERE workflow_id = :workflowId
                ORDER BY version_number DESC
                LIMIT :limit OFFSET :offset
                """, new MapSqlParameterSource()
                .addValue("workflowId", workflowId)
                .addValue("offset", offset)
                .addValue("limit", limit),
                (rs, rowNum) -> mapRow(rs));
    }

    @Override
    public long countByWorkflowId(String workflowId) {
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) FROM workflow_version WHERE workflow_id = :workflowId
                """, Map.of("workflowId", workflowId), Long.class);
        return total == null ? 0L : total;
    }

    @Override
    public int getMaxVersionNumber(String workflowId) {
        Integer max = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(version_number), 0) FROM workflow_version WHERE workflow_id = :workflowId
                """, Map.of("workflowId", workflowId), Integer.class);
        return max == null ? 0 : max;
    }

    @Override
    public void clearPublishedByWorkflowId(String workflowId) {
        jdbcTemplate.update("""
                UPDATE workflow_version SET published = FALSE WHERE workflow_id = :workflowId AND published = TRUE
                """, Map.of("workflowId", workflowId));
    }

    @Override
    public void setPublished(String workflowId, int versionNumber, boolean published) {
        jdbcTemplate.update("""
                UPDATE workflow_version SET published = :published
                WHERE workflow_id = :workflowId AND version_number = :versionNumber
                """, Map.of("workflowId", workflowId, "versionNumber", versionNumber, "published", published));
    }

    private WorkflowVersion mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return WorkflowVersion.builder()
                .versionId(rs.getString("version_id"))
                .workflowId(rs.getString("workflow_id"))
                .tenantId(rs.getString("tenant_id"))
                .versionNumber(rs.getInt("version_number"))
                .definitionJson(rs.getString("definition_json"))
                .changeSummary(rs.getString("change_summary"))
                .published(rs.getBoolean("published"))
                .createdBy(rs.getString("created_by"))
                .createdAt(rs.getLong("created_at"))
                .build();
    }

    private MapSqlParameterSource toParams(WorkflowVersion version) {
        return new MapSqlParameterSource()
                .addValue("versionId", version.getVersionId())
                .addValue("workflowId", version.getWorkflowId())
                .addValue("tenantId", version.getTenantId())
                .addValue("versionNumber", version.getVersionNumber())
                .addValue("definitionJson", version.getDefinitionJson())
                .addValue("changeSummary", version.getChangeSummary())
                .addValue("published", version.isPublished())
                .addValue("createdBy", version.getCreatedBy())
                .addValue("createdAt", version.getCreatedAt());
    }
}
