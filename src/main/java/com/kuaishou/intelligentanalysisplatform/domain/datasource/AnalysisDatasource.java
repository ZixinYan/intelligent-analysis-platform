package com.kuaishou.intelligentanalysisplatform.domain.datasource;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceStatus;
import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisDatasource {
    private String id;
    private String tenantId;
    private String name;
    private DatasourceType type;
    private String host;
    private Integer port;
    private String database;
    private String username;
    private String encryptedPassword;
    private Map<String, String> jdbcOptions;
    private DatasourceStatus status;
    private Boolean readonly;
    private Long createdAt;
    private Long updatedAt;
    private String createdBy;

    public static AnalysisDatasource create(String tenantId, String name, DatasourceType type, String host, Integer port,
                                            String database, String username, String encryptedPassword,
                                            Map<String, String> jdbcOptions, Boolean readonly, String createdBy) {
        enforceReadonly(readonly);
        long now = Instant.now().toEpochMilli();
        return AnalysisDatasource.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .name(name)
                .type(type)
                .host(host)
                .port(port)
                .database(database)
                .username(username)
                .encryptedPassword(encryptedPassword)
                .jdbcOptions(jdbcOptions)
                .status(DatasourceStatus.ACTIVE)
                .readonly(Boolean.TRUE)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(createdBy)
                .build();
    }

    public void applyUpdate(String name, DatasourceType type, String host, Integer port, String database,
                            String username, String encryptedPassword, Map<String, String> jdbcOptions,
                            Boolean readonly) {
        if (readonly != null) {
            enforceReadonly(readonly);
            this.readonly = Boolean.TRUE;
        }
        if (name != null) {
            this.name = name;
        }
        if (type != null) {
            this.type = type;
        }
        if (host != null) {
            this.host = host;
        }
        if (port != null) {
            this.port = port;
        }
        if (database != null) {
            this.database = database;
        }
        if (username != null) {
            this.username = username;
        }
        if (encryptedPassword != null) {
            this.encryptedPassword = encryptedPassword;
        }
        if (jdbcOptions != null) {
            this.jdbcOptions = jdbcOptions;
        }
        this.updatedAt = Instant.now().toEpochMilli();
    }

    public void markReachable() {
        this.status = DatasourceStatus.ACTIVE;
        this.updatedAt = Instant.now().toEpochMilli();
    }

    public void markUnreachable() {
        this.status = DatasourceStatus.UNREACHABLE;
        this.updatedAt = Instant.now().toEpochMilli();
    }

    private static void enforceReadonly(Boolean readonly) {
        if (!Boolean.TRUE.equals(readonly)) {
            throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "datasource must be readonly");
        }
    }
}
