package com.kuaishou.intelligentanalysisplatform.infra.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.kuaishou.intelligentanalysisplatform.domain.workflow.WorkflowDefinition;
import com.kuaishou.intelligentanalysisplatform.domain.workflow.WorkflowDefinitionRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkflowDefinitionRepository implements WorkflowDefinitionRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcWorkflowDefinitionRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(WorkflowDefinition definition) {
        jdbcTemplate.update("""
                INSERT INTO workflow_definition (
                    workflow_id, tenant_id, workflow_name, definition_json, operator_id, created_at, updated_at
                ) VALUES (
                    :workflowId, :tenantId, :workflowName, :definitionJson, :operatorId, :createdAt, :updatedAt
                )
                """, toParams(definition));
    }

    @Override
    public void update(WorkflowDefinition definition) {
        jdbcTemplate.update("""
                UPDATE workflow_definition
                SET workflow_name = :workflowName,
                    definition_json = :definitionJson,
                    operator_id = :operatorId,
                    updated_at = :updatedAt
                WHERE workflow_id = :workflowId
                  AND tenant_id = :tenantId
                """, toParams(definition));
    }

    @Override
    public Optional<WorkflowDefinition> findByIdAndTenantId(String workflowId, String tenantId) {
        return jdbcTemplate.query("""
                SELECT workflow_id, tenant_id, workflow_name, definition_json, operator_id, created_at, updated_at
                FROM workflow_definition
                WHERE workflow_id = :workflowId
                  AND tenant_id = :tenantId
                """, Map.of("workflowId", workflowId, "tenantId", tenantId), rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(WorkflowDefinition.builder()
                    .workflowId(rs.getString("workflow_id"))
                    .tenantId(rs.getString("tenant_id"))
                    .workflowName(rs.getString("workflow_name"))
                    .definitionJson(rs.getString("definition_json"))
                    .operatorId(rs.getString("operator_id"))
                    .createdAt(rs.getLong("created_at"))
                    .updatedAt(rs.getLong("updated_at"))
                    .build());
        });
    }

    @Override
    public long countByTenantId(String tenantId) {
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM workflow_definition
                WHERE tenant_id = :tenantId
                """, Map.of("tenantId", tenantId), Long.class);
        return total == null ? 0L : total;
    }

    @Override
    public List<WorkflowDefinition> findByTenantId(String tenantId, int offset, int limit) {
        return jdbcTemplate.query("""
                SELECT workflow_id, tenant_id, workflow_name, definition_json, operator_id, created_at, updated_at
                FROM workflow_definition
                WHERE tenant_id = :tenantId
                ORDER BY updated_at DESC
                LIMIT :limit OFFSET :offset
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("offset", offset)
                .addValue("limit", limit), (rs, rowNum) -> WorkflowDefinition.builder()
                .workflowId(rs.getString("workflow_id"))
                .tenantId(rs.getString("tenant_id"))
                .workflowName(rs.getString("workflow_name"))
                .definitionJson(rs.getString("definition_json"))
                .operatorId(rs.getString("operator_id"))
                .createdAt(rs.getLong("created_at"))
                .updatedAt(rs.getLong("updated_at"))
                .build());
    }

    private MapSqlParameterSource toParams(WorkflowDefinition definition) {
        return new MapSqlParameterSource()
                .addValue("workflowId", definition.getWorkflowId())
                .addValue("tenantId", definition.getTenantId())
                .addValue("workflowName", definition.getWorkflowName())
                .addValue("definitionJson", definition.getDefinitionJson())
                .addValue("operatorId", definition.getOperatorId())
                .addValue("createdAt", definition.getCreatedAt())
                .addValue("updatedAt", definition.getUpdatedAt());
    }
}
