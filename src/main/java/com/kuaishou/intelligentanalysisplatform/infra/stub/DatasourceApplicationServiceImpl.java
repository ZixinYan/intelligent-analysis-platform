package com.kuaishou.intelligentanalysisplatform.infra.stub;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import com.kuaishou.intelligentanalysisplatform.application.DatasourceApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.security.PermissionChecker;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.common.response.PageResult;
import com.kuaishou.intelligentanalysisplatform.common.security.TenantSecurityGuard;
import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceType;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceCreateRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceQueryAccessDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceQueryRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceTestConnectionRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceTestConnectionResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceUpdateRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.domain.datasource.AnalysisDatasource;
import com.kuaishou.intelligentanalysisplatform.domain.datasource.DatasourceRepository;
import com.kuaishou.intelligentanalysisplatform.infra.security.CredentialEncryptor;
import org.springframework.stereotype.Service;

@Service
public class DatasourceApplicationServiceImpl implements DatasourceApplicationService {
    private final DatasourceRepository datasourceRepository;
    private final CredentialEncryptor credentialEncryptor;
    private final PermissionChecker permissionChecker;

    public DatasourceApplicationServiceImpl(DatasourceRepository datasourceRepository,
                                             CredentialEncryptor credentialEncryptor,
                                             PermissionChecker permissionChecker) {
        this.datasourceRepository = datasourceRepository;
        this.credentialEncryptor = credentialEncryptor;
        this.permissionChecker = permissionChecker;
    }

    @Override
    public DatasourceDTO create(DatasourceCreateRequestDTO request) {
        RequestContextDTO context = request.getContext();
        permissionChecker.requireWrite(context);
        TenantSecurityGuard.requireContext(context);
        ensureNameNotExists(context.getTenantId(), request.getName(), null);
        AnalysisDatasource datasource = AnalysisDatasource.create(
                context.getTenantId(),
                request.getName(),
                request.getType(),
                request.getHost(),
                request.getPort(),
                request.getDatabase(),
                request.getUsername(),
                credentialEncryptor.encrypt(request.getPassword()),
                request.getJdbcOptions(),
                request.getReadonly(),
                context.getUserId()
        );
        return toDTO(datasourceRepository.save(datasource));
    }

    @Override
    public DatasourceDTO update(String id, DatasourceUpdateRequestDTO request) {
        RequestContextDTO context = request.getContext();
        permissionChecker.requireWrite(context);
        AnalysisDatasource datasource = requireOwnedDatasource(id, context);
        ensureNameNotExists(context.getTenantId(), request.getName(), id);
        datasource.applyUpdate(
                request.getName(),
                request.getType(),
                request.getHost(),
                request.getPort(),
                request.getDatabase(),
                request.getUsername(),
                request.getPassword() == null ? null : credentialEncryptor.encrypt(request.getPassword()),
                request.getJdbcOptions(),
                request.getReadonly()
        );
        return toDTO(datasourceRepository.save(datasource));
    }

    @Override
    public void delete(String id, RequestContextDTO context) {
        permissionChecker.requireDelete(context);
        AnalysisDatasource datasource = requireOwnedDatasource(id, context);
        datasourceRepository.deleteByIdAndTenantId(datasource.getId(), datasource.getTenantId());
    }

    @Override
    public DatasourceDTO getById(String id, RequestContextDTO context) {
        permissionChecker.requireRead(context);
        return toDTO(requireOwnedDatasource(id, context));
    }

    @Override
    public PageResult<DatasourceDTO> list(DatasourceQueryRequestDTO request) {
        RequestContextDTO context = request.getContext();
        permissionChecker.requireRead(context);
        DatasourceType type;
        try {
            type = request.resolveType();
        } catch (IllegalArgumentException e) {
            throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "invalid datasource type", e.getMessage(), null, false);
        }
        PageResult<AnalysisDatasource> pageResult = datasourceRepository.findByTenant(
                context.getTenantId(),
                type,
                request.getKeyword(),
                request.getPage() == null ? 1 : request.getPage(),
                request.getPageSize() == null ? 20 : request.getPageSize()
        );
        return PageResult.<DatasourceDTO>builder()
                .items(pageResult.getItems().stream().map(this::toDTO).toList())
                .total(pageResult.getTotal())
                .page(pageResult.getPage())
                .pageSize(pageResult.getPageSize())
                .build();
    }

    @Override
    public DatasourceTestConnectionResultDTO testConnection(DatasourceTestConnectionRequestDTO request) {
        permissionChecker.requireTestConnection(request.getContext());
        AnalysisDatasource datasource = requireOwnedDatasource(request.getDatasourceId(), request.getContext());

        String password = credentialEncryptor.decrypt(datasource.getEncryptedPassword());
        String jdbcUrl = buildJdbcUrl(datasource);

        long startMs = System.currentTimeMillis();
        try (Connection connection = DriverManager.getConnection(jdbcUrl, datasource.getUsername(), password)) {
            long latencyMs = System.currentTimeMillis() - startMs;
            DatabaseMetaData metaData = connection.getMetaData();
            String serverVersion = metaData.getDatabaseProductName() + " " + metaData.getDatabaseProductVersion();
            datasource.markReachable();
            datasourceRepository.save(datasource);
            return DatasourceTestConnectionResultDTO.builder()
                    .success(Boolean.TRUE)
                    .latencyMs(latencyMs)
                    .message("connection ok")
                    .serverVersion(serverVersion)
                    .build();
        } catch (SQLException e) {
            datasource.markUnreachable();
            datasourceRepository.save(datasource);
            return DatasourceTestConnectionResultDTO.builder()
                    .success(Boolean.FALSE)
                    .latencyMs(System.currentTimeMillis() - startMs)
                    .message("connection failed: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public DatasourceQueryAccessDTO getQueryAccess(String datasourceId, RequestContextDTO context) {
        permissionChecker.requireTestConnection(context);
        AnalysisDatasource datasource = requireOwnedDatasource(datasourceId, context);
        return DatasourceQueryAccessDTO.builder()
                .id(datasource.getId())
                .tenantId(datasource.getTenantId())
                .type(datasource.getType())
                .host(datasource.getHost())
                .port(datasource.getPort())
                .database(datasource.getDatabase())
                .username(datasource.getUsername())
                .encryptedPassword(datasource.getEncryptedPassword())
                .jdbcOptions(datasource.getJdbcOptions())
                .readonly(datasource.getReadonly())
                .build();
    }

    @Override
    public List<String> listTables(String datasourceId, RequestContextDTO context) {
        return List.of();
    }

    private AnalysisDatasource requireOwnedDatasource(String id, RequestContextDTO context) {
        TenantSecurityGuard.requireContext(context);
        return datasourceRepository.findById(id)
                .map(item -> {
                    TenantSecurityGuard.requireSameTenant(item.getTenantId(), context);
                    return item;
                })
                .orElseThrow(() -> new BaseBusinessException(ErrorCode.DATASOURCE_NOT_FOUND, "datasource not found"));
    }

    private void ensureNameNotExists(String tenantId, String name, String excludeId) {
        if (datasourceRepository.existsByName(tenantId, name, excludeId)) {
            throw new BaseBusinessException(ErrorCode.DATASOURCE_ALREADY_EXISTS, "datasource already exists");
        }
    }

    private String buildJdbcUrl(AnalysisDatasource datasource) {
        String database = datasource.getDatabase() == null ? "" : "/" + datasource.getDatabase();
        return switch (datasource.getType()) {
            case MYSQL -> "jdbc:mysql://" + datasource.getHost() + ":" + datasource.getPort() + database;
            case POSTGRES -> "jdbc:postgresql://" + datasource.getHost() + ":" + datasource.getPort() + database;
            case CLICKHOUSE -> "jdbc:clickhouse://" + datasource.getHost() + ":" + datasource.getPort() + database;
        };
    }

    private DatasourceDTO toDTO(AnalysisDatasource datasource) {
        return DatasourceDTO.builder()
                .id(datasource.getId())
                .tenantId(datasource.getTenantId())
                .name(datasource.getName())
                .type(datasource.getType())
                .host(datasource.getHost())
                .port(datasource.getPort())
                .database(datasource.getDatabase())
                .username(datasource.getUsername())
                .jdbcOptions(datasource.getJdbcOptions())
                .status(datasource.getStatus())
                .readonly(datasource.getReadonly())
                .createdAt(datasource.getCreatedAt())
                .updatedAt(datasource.getUpdatedAt())
                .createdBy(datasource.getCreatedBy())
                .build();
    }
}
