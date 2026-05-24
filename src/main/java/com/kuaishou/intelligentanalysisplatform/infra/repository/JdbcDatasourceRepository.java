package com.kuaishou.intelligentanalysisplatform.infra.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.common.response.PageResult;
import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceStatus;
import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceType;
import com.kuaishou.intelligentanalysisplatform.domain.datasource.AnalysisDatasource;
import com.kuaishou.intelligentanalysisplatform.domain.datasource.DatasourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

// JDBC implementation of DatasourceRepository backed by datasource_config table
@Repository
public class JdbcDatasourceRepository implements DatasourceRepository {
    private static final Logger log = LoggerFactory.getLogger(JdbcDatasourceRepository.class);
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcDatasourceRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<AnalysisDatasource> findById(String id) {
        return jdbcTemplate.query("""
                SELECT id, tenant_id, name, type, host, port, database_name, username,
                       encrypted_password, jdbc_options, status, readonly_flag, created_at, updated_at, created_by
                FROM datasource_config
                WHERE id = :id
                """, Map.of("id", id), rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(mapRow(rs));
        });
    }

    @Override
    public Optional<AnalysisDatasource> findByIdAndTenantId(String id, String tenantId) {
        return jdbcTemplate.query("""
                SELECT id, tenant_id, name, type, host, port, database_name, username,
                       encrypted_password, jdbc_options, status, readonly_flag, created_at, updated_at, created_by
                FROM datasource_config
                WHERE id = :id AND tenant_id = :tenantId
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tenantId", tenantId), rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(mapRow(rs));
        });
    }

    @Override
    public PageResult<AnalysisDatasource> findByTenant(String tenantId, DatasourceType type, String keyword, int page, int pageSize) {
        StringBuilder sqlBuilder = new StringBuilder("""
                SELECT id, tenant_id, name, type, host, port, database_name, username,
                       encrypted_password, jdbc_options, status, readonly_flag, created_at, updated_at, created_by
                FROM datasource_config
                WHERE tenant_id = :tenantId
                """);
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", tenantId);

        if (type != null) {
            sqlBuilder.append(" AND type = :type");
            params.addValue("type", type.name());
        }
        if (keyword != null && !keyword.isBlank()) {
            sqlBuilder.append(" AND (name LIKE :keyword OR host LIKE :keyword OR database_name LIKE :keyword)");
            params.addValue("keyword", "%" + keyword + "%");
        }

        String countSql = "SELECT COUNT(*) FROM datasource_config WHERE tenant_id = :tenantId"
                + (type != null ? " AND type = :type" : "")
                + (keyword != null && !keyword.isBlank() ? " AND (name LIKE :keyword OR host LIKE :keyword OR database_name LIKE :keyword)" : "");
        Long total = jdbcTemplate.queryForObject(countSql, params, Long.class);
        long totalCount = total == null ? 0L : total;

        int normalizedPage = Math.max(page, 1);
        int normalizedPageSize = Math.max(pageSize, 1);
        int offset = (normalizedPage - 1) * normalizedPageSize;

        sqlBuilder.append(" ORDER BY created_at DESC LIMIT :limit OFFSET :offset");
        params.addValue("limit", normalizedPageSize);
        params.addValue("offset", offset);

        var items = jdbcTemplate.query(sqlBuilder.toString(), params, (rs, rowNum) -> mapRow(rs));

        return PageResult.<AnalysisDatasource>builder()
                .items(items)
                .total(totalCount)
                .page(normalizedPage)
                .pageSize(normalizedPageSize)
                .build();
    }

    @Override
    public AnalysisDatasource save(AnalysisDatasource datasource) {
        boolean exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM datasource_config WHERE id = :id",
                Map.of("id", datasource.getId()), Integer.class) > 0;

        if (exists) {
            jdbcTemplate.update("""
                    UPDATE datasource_config
                    SET name = :name, type = :type, host = :host, port = :port, database_name = :database,
                        username = :username, encrypted_password = :encryptedPassword,
                        jdbc_options = :jdbcOptions, status = :status, readonly_flag = :readonly,
                        updated_at = :updatedAt
                    WHERE id = :id
                    """, toUpdateParams(datasource));
        } else {
            jdbcTemplate.update("""
                    INSERT INTO datasource_config (
                        id, tenant_id, name, type, host, port, database_name, username,
                        encrypted_password, jdbc_options, status, readonly_flag,
                        created_at, updated_at, created_by
                    ) VALUES (
                        :id, :tenantId, :name, :type, :host, :port, :database, :username,
                        :encryptedPassword, :jdbcOptions, :status, :readonly,
                        :createdAt, :updatedAt, :createdBy
                    )
                    """, toInsertParams(datasource));
        }
        return datasource;
    }

    @Override
    public void deleteByIdAndTenantId(String id, String tenantId) {
        jdbcTemplate.update(
                "DELETE FROM datasource_config WHERE id = :id AND tenant_id = :tenantId",
                new MapSqlParameterSource().addValue("id", id).addValue("tenantId", tenantId));
    }

    @Override
    public boolean existsByIdAndTenantId(String id, String tenantId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM datasource_config WHERE id = :id AND tenant_id = :tenantId",
                new MapSqlParameterSource().addValue("id", id).addValue("tenantId", tenantId), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public boolean existsByName(String tenantId, String name, String excludeId) {
        if (name == null || name.isBlank()) {
            return false;
        }
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM datasource_config WHERE tenant_id = :tenantId AND LOWER(name) = LOWER(:name)");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("name", name);
        if (excludeId != null) {
            sql.append(" AND id != :excludeId");
            params.addValue("excludeId", excludeId);
        }
        Integer count = jdbcTemplate.queryForObject(sql.toString(), params, Integer.class);
        return count != null && count > 0;
    }

    private AnalysisDatasource mapRow(ResultSet rs) throws SQLException {
        return AnalysisDatasource.builder()
                .id(rs.getString("id"))
                .tenantId(rs.getString("tenant_id"))
                .name(rs.getString("name"))
                .type(DatasourceType.valueOf(rs.getString("type")))
                .host(rs.getString("host"))
                .port(rs.getInt("port"))
                .database(rs.getString("database_name"))
                .username(rs.getString("username"))
                .encryptedPassword(rs.getString("encrypted_password"))
                .jdbcOptions(parseJsonOptions(rs.getString("jdbc_options")))
                .status(DatasourceStatus.valueOf(rs.getString("status")))
                .readonly(rs.getBoolean("readonly_flag"))
                .createdAt(rs.getLong("created_at"))
                .updatedAt(rs.getLong("updated_at"))
                .createdBy(rs.getString("created_by"))
                .build();
    }

    private Map<String, String> parseJsonOptions(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse jdbc_options JSON: {}", json, e);
            return Collections.emptyMap();
        }
    }

    private String toJsonOptions(Map<String, String> jdbcOptions) {
        if (jdbcOptions == null || jdbcOptions.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(jdbcOptions);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize jdbc_options", e);
            return "{}";
        }
    }

    private MapSqlParameterSource toInsertParams(AnalysisDatasource ds) {
        return new MapSqlParameterSource()
                .addValue("id", ds.getId())
                .addValue("tenantId", ds.getTenantId())
                .addValue("name", ds.getName())
                .addValue("type", ds.getType().name())
                .addValue("host", ds.getHost())
                .addValue("port", ds.getPort())
                .addValue("database", ds.getDatabase())
                .addValue("username", ds.getUsername())
                .addValue("encryptedPassword", ds.getEncryptedPassword())
                .addValue("jdbcOptions", toJsonOptions(ds.getJdbcOptions()))
                .addValue("status", ds.getStatus().name())
                .addValue("readonly", Boolean.TRUE.equals(ds.getReadonly()))
                .addValue("createdAt", ds.getCreatedAt())
                .addValue("updatedAt", ds.getUpdatedAt())
                .addValue("createdBy", ds.getCreatedBy());
    }

    private MapSqlParameterSource toUpdateParams(AnalysisDatasource ds) {
        return new MapSqlParameterSource()
                .addValue("id", ds.getId())
                .addValue("name", ds.getName())
                .addValue("type", ds.getType().name())
                .addValue("host", ds.getHost())
                .addValue("port", ds.getPort())
                .addValue("database", ds.getDatabase())
                .addValue("username", ds.getUsername())
                .addValue("encryptedPassword", ds.getEncryptedPassword())
                .addValue("jdbcOptions", toJsonOptions(ds.getJdbcOptions()))
                .addValue("status", ds.getStatus().name())
                .addValue("readonly", Boolean.TRUE.equals(ds.getReadonly()))
                .addValue("updatedAt", ds.getUpdatedAt());
    }
}
