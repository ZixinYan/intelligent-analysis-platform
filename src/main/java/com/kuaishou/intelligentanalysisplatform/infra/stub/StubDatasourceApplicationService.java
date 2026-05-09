package com.kuaishou.intelligentanalysisplatform.infra.stub;

import java.util.Locale;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.ConnectorFactory;
import com.kuaishou.intelligentanalysisplatform.infra.security.CredentialEncryptor;
import org.springframework.stereotype.Service;

@Service
public class StubDatasourceApplicationService implements DatasourceApplicationService {
    private final DatasourceRepository datasourceRepository;
    private final CredentialEncryptor credentialEncryptor;
    private final PermissionChecker permissionChecker;
    private final ConnectorFactory connectorFactory;

    public StubDatasourceApplicationService(DatasourceRepository datasourceRepository,
                                            CredentialEncryptor credentialEncryptor,
                                            PermissionChecker permissionChecker,
                                            ConnectorFactory connectorFactory) {
        this.datasourceRepository = datasourceRepository;
        this.credentialEncryptor = credentialEncryptor;
        this.permissionChecker = permissionChecker;
        this.connectorFactory = connectorFactory;
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
        validateConnectivity(datasource);
        long latencyMs = ThreadLocalRandom.current().nextLong(5, 50);
        datasource.markReachable();
        datasourceRepository.save(datasource);
        return DatasourceTestConnectionResultDTO.builder()
                .success(Boolean.TRUE)
                .latencyMs(latencyMs)
                .message("connection ok")
                .serverVersion(datasource.getType() == DatasourceType.MYSQL ? "mysql-8.0-stub" : "postgres-15-stub")
                .build();
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
        permissionChecker.requireRead(context);
        AnalysisDatasource datasource = requireOwnedDatasource(datasourceId, context);
        return connectorFactory.create(datasource).listTables(datasource);
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

    private void validateConnectivity(AnalysisDatasource datasource) {
        if (datasource.getPort() == null || datasource.getPort() <= 0 || datasource.getPort() > 65535) {
            datasource.markUnreachable();
            datasourceRepository.save(datasource);
            throw new BaseBusinessException(ErrorCode.DATASOURCE_CONNECTION_FAILED, "datasource connection failed", "invalid port", null, false);
        }
        if (containsFailureKeyword(datasource.getHost()) || containsFailureKeyword(datasource.getDatabase())
                || containsFailureKeyword(datasource.getUsername())) {
            datasource.markUnreachable();
            datasourceRepository.save(datasource);
            throw new BaseBusinessException(ErrorCode.DATASOURCE_CONNECTION_FAILED, "datasource connection failed", "simulated connection failure", null, false);
        }
    }

    private boolean containsFailureKeyword(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains("fail");
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
