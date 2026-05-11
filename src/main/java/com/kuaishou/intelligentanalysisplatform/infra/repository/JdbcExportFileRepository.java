package com.kuaishou.intelligentanalysisplatform.infra.repository;

import java.util.Map;
import java.util.Optional;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ExportFormat;
import com.kuaishou.intelligentanalysisplatform.domain.export.ExportFile;
import com.kuaishou.intelligentanalysisplatform.domain.export.ExportFileRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcExportFileRepository implements ExportFileRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcExportFileRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(ExportFile file) {
        jdbcTemplate.update("""
                INSERT INTO export_file (
                    file_id, tenant_id, file_name, format, storage_path,
                    file_size_bytes, row_count, created_at, expires_at
                ) VALUES (
                    :fileId, :tenantId, :fileName, :format, :storagePath,
                    :fileSizeBytes, :rowCount, :createdAt, :expiresAt
                )
                """, toParams(file));
    }

    @Override
    public Optional<ExportFile> findById(String fileId) {
        return jdbcTemplate.query("""
                SELECT file_id, tenant_id, file_name, format, storage_path,
                       file_size_bytes, row_count, created_at, expires_at
                FROM export_file
                WHERE file_id = :fileId
                """, Map.of("fileId", fileId), rs -> {
            if (!rs.next()) return Optional.empty();
            return Optional.of(toExportFile(rs));
        });
    }

    @Override
    public Optional<ExportFile> findByIdAndTenantId(String fileId, String tenantId) {
        return jdbcTemplate.query("""
                SELECT file_id, tenant_id, file_name, format, storage_path,
                       file_size_bytes, row_count, created_at, expires_at
                FROM export_file
                WHERE file_id = :fileId
                  AND tenant_id = :tenantId
                """, new MapSqlParameterSource()
                .addValue("fileId", fileId)
                .addValue("tenantId", tenantId), rs -> {
            if (!rs.next()) return Optional.empty();
            return Optional.of(toExportFile(rs));
        });
    }

    @Override
    public void deleteExpired(long beforeTimestamp) {
        jdbcTemplate.update("""
                DELETE FROM export_file WHERE expires_at < :beforeTimestamp
                """, Map.of("beforeTimestamp", beforeTimestamp));
    }

    private ExportFile toExportFile(java.sql.ResultSet rs) throws java.sql.SQLException {
        String formatStr = rs.getString("format");
        return ExportFile.builder()
                .fileId(rs.getString("file_id"))
                .tenantId(rs.getString("tenant_id"))
                .fileName(rs.getString("file_name"))
                .format(formatStr != null ? ExportFormat.valueOf(formatStr) : null)
                .storagePath(rs.getString("storage_path"))
                .fileSizeBytes(rs.getObject("file_size_bytes", Long.class))
                .rowCount(rs.getObject("row_count", Integer.class))
                .createdAt(rs.getLong("created_at"))
                .expiresAt(rs.getObject("expires_at", Long.class))
                .build();
    }

    private MapSqlParameterSource toParams(ExportFile file) {
        return new MapSqlParameterSource()
                .addValue("fileId", file.getFileId())
                .addValue("tenantId", file.getTenantId())
                .addValue("fileName", file.getFileName())
                .addValue("format", file.getFormat() == null ? null : file.getFormat().name())
                .addValue("storagePath", file.getStoragePath())
                .addValue("fileSizeBytes", file.getFileSizeBytes())
                .addValue("rowCount", file.getRowCount())
                .addValue("createdAt", file.getCreatedAt())
                .addValue("expiresAt", file.getExpiresAt());
    }
}
