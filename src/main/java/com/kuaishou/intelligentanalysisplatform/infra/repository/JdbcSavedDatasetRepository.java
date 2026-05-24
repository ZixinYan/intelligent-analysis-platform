package com.kuaishou.intelligentanalysisplatform.infra.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetStatDTO;
import com.kuaishou.intelligentanalysisplatform.domain.dataset.SavedDataset;
import com.kuaishou.intelligentanalysisplatform.domain.dataset.SavedDatasetRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSavedDatasetRepository implements SavedDatasetRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcSavedDatasetRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(SavedDataset dataset) {
        jdbcTemplate.update("""
                INSERT INTO saved_dataset (
                    dataset_id, tenant_id, name, description, created_by,
                    schema_json, stat_json, rows_json,
                    source_workflow_id, source_node_id,
                    created_at, updated_at
                ) VALUES (
                    :datasetId, :tenantId, :name, :description, :createdBy,
                    :schemaJson, :statJson, :rowsJson,
                    :sourceWorkflowId, :sourceNodeId,
                    :createdAt, :updatedAt
                )
                """, toParams(dataset));
    }

    @Override
    public void update(SavedDataset dataset) {
        jdbcTemplate.update("""
                UPDATE saved_dataset
                SET name = :name,
                    description = :description,
                    updated_at = :updatedAt
                WHERE dataset_id = :datasetId
                  AND tenant_id = :tenantId
                """, new MapSqlParameterSource()
                .addValue("datasetId", dataset.getDatasetId())
                .addValue("tenantId", dataset.getTenantId())
                .addValue("name", dataset.getName())
                .addValue("description", dataset.getDescription())
                .addValue("updatedAt", dataset.getUpdatedAt()));
    }

    @Override
    public Optional<SavedDataset> findById(String datasetId) {
        return jdbcTemplate.query("""
                SELECT dataset_id, tenant_id, name, description, created_by,
                       schema_json, stat_json, rows_json,
                       source_workflow_id, source_node_id,
                       created_at, updated_at
                FROM saved_dataset
                WHERE dataset_id = :datasetId
                """, Map.of("datasetId", datasetId), rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(toSavedDataset(rs, true));
        });
    }

    @Override
    public Optional<SavedDataset> findByIdAndTenantId(String datasetId, String tenantId) {
        return jdbcTemplate.query("""
                SELECT dataset_id, tenant_id, name, description, created_by,
                       schema_json, stat_json, rows_json,
                       source_workflow_id, source_node_id,
                       created_at, updated_at
                FROM saved_dataset
                WHERE dataset_id = :datasetId
                  AND tenant_id = :tenantId
                """, new MapSqlParameterSource()
                .addValue("datasetId", datasetId)
                .addValue("tenantId", tenantId), rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(toSavedDataset(rs, true));
        });
    }

    @Override
    public List<SavedDataset> findSummaryByTenantId(String tenantId, int limit, long beforeUpdatedAt) {
        return jdbcTemplate.query("""
                SELECT dataset_id, tenant_id, name, description, created_by,
                       schema_json, stat_json,
                       source_workflow_id, source_node_id,
                       created_at, updated_at
                FROM saved_dataset
                WHERE tenant_id = :tenantId
                  AND updated_at < :beforeUpdatedAt
                ORDER BY updated_at DESC
                LIMIT :limit
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("beforeUpdatedAt", beforeUpdatedAt)
                .addValue("limit", limit),
                (rs, rowNum) -> toSavedDataset(rs, false));
    }

    @Override
    public void deleteByIdAndTenantId(String datasetId, String tenantId) {
        jdbcTemplate.update("""
                DELETE FROM saved_dataset
                WHERE dataset_id = :datasetId
                  AND tenant_id = :tenantId
                """, new MapSqlParameterSource()
                .addValue("datasetId", datasetId)
                .addValue("tenantId", tenantId));
    }

    @Override
    public boolean existsByIdAndTenantId(String datasetId, String tenantId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM saved_dataset
                WHERE dataset_id = :datasetId
                  AND tenant_id = :tenantId
                """, new MapSqlParameterSource()
                .addValue("datasetId", datasetId)
                .addValue("tenantId", tenantId), Integer.class);
        return count != null && count > 0;
    }

    private SavedDataset toSavedDataset(ResultSet rs, boolean includeRows) throws SQLException {
        return SavedDataset.builder()
                .datasetId(rs.getString("dataset_id"))
                .tenantId(rs.getString("tenant_id"))
                .name(rs.getString("name"))
                .description(rs.getString("description"))
                .createdBy(rs.getString("created_by"))
                .schema(parseSchema(readText(rs, "schema_json")))
                .stat(parseStat(readText(rs, "stat_json")))
                .rows(includeRows ? parseRows(readText(rs, "rows_json")) : null)
                .sourceWorkflowId(rs.getString("source_workflow_id"))
                .sourceNodeId(rs.getString("source_node_id"))
                .createdAt(rs.getLong("created_at"))
                .updatedAt(rs.getLong("updated_at"))
                .build();
    }

    private MapSqlParameterSource toParams(SavedDataset dataset) {
        return new MapSqlParameterSource()
                .addValue("datasetId", dataset.getDatasetId())
                .addValue("tenantId", dataset.getTenantId())
                .addValue("name", dataset.getName())
                .addValue("description", dataset.getDescription())
                .addValue("createdBy", dataset.getCreatedBy())
                .addValue("schemaJson", serializeJson(dataset.getSchema()))
                .addValue("statJson", serializeJson(dataset.getStat()))
                .addValue("rowsJson", serializeJson(dataset.getRows()))
                .addValue("sourceWorkflowId", dataset.getSourceWorkflowId())
                .addValue("sourceNodeId", dataset.getSourceNodeId())
                .addValue("createdAt", dataset.getCreatedAt())
                .addValue("updatedAt", dataset.getUpdatedAt());
    }

    private String serializeJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize object to JSON", e);
        }
    }

    private DatasetSchemaDTO parseSchema(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, DatasetSchemaDTO.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private DatasetStatDTO parseStat(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, DatasetStatDTO.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private List<Map<String, Object>> parseRows(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    private String readText(ResultSet rs, String column) throws SQLException {
        java.sql.Clob clob = rs.getClob(column);
        if (clob == null) {
            return rs.getString(column);
        }
        return clob.getSubString(1, (int) clob.length());
    }
}
