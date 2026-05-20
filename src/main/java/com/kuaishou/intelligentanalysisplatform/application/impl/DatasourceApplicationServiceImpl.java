package com.kuaishou.intelligentanalysisplatform.application.impl;

import java.util.List;
import java.util.Map;

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
import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.TableSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.domain.datasource.AnalysisDatasource;
import com.kuaishou.intelligentanalysisplatform.domain.datasource.DatasourceRepository;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.Connector;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.ConnectorFactory;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.HealthCheckResult;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.QueryCommand;
import com.kuaishou.intelligentanalysisplatform.infra.connector.pool.HikariPoolRegistry;
import com.kuaishou.intelligentanalysisplatform.infra.security.CredentialEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DatasourceApplicationServiceImpl implements DatasourceApplicationService {
    private static final Logger log = LoggerFactory.getLogger(DatasourceApplicationServiceImpl.class);

    private final DatasourceRepository datasourceRepository;
    private final CredentialEncryptor credentialEncryptor;
    private final PermissionChecker permissionChecker;
    private final ConnectorFactory connectorFactory;
    private final HikariPoolRegistry hikariPoolRegistry;

    public DatasourceApplicationServiceImpl(DatasourceRepository datasourceRepository,
                                             CredentialEncryptor credentialEncryptor,
                                             PermissionChecker permissionChecker,
                                             ConnectorFactory connectorFactory,
                                             HikariPoolRegistry hikariPoolRegistry) {
        this.datasourceRepository = datasourceRepository;
        this.credentialEncryptor = credentialEncryptor;
        this.permissionChecker = permissionChecker;
        this.connectorFactory = connectorFactory;
        this.hikariPoolRegistry = hikariPoolRegistry;
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
        DatasourceDTO result = toDTO(datasourceRepository.save(datasource));
        log.info("Datasource updated, evicting connection pool: datasourceId={}, tenantId={}, type={}",
                datasource.getId(), datasource.getTenantId(), datasource.getType());
        hikariPoolRegistry.evict(datasource.getId());
        return result;
    }

    @Override
    public void delete(String id, RequestContextDTO context) {
        permissionChecker.requireDelete(context);
        AnalysisDatasource datasource = requireOwnedDatasource(id, context);
        datasourceRepository.deleteByIdAndTenantId(datasource.getId(), datasource.getTenantId());
        log.info("Datasource deleted, evicting connection pool: datasourceId={}, tenantId={}, type={}",
                datasource.getId(), datasource.getTenantId(), datasource.getType());
        hikariPoolRegistry.evict(datasource.getId());
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
        log.info("Testing datasource connection: datasourceId={}, tenantId={}, type={}, host={}, port={}",
                datasource.getId(), datasource.getTenantId(), datasource.getType(), datasource.getHost(), datasource.getPort());
        Connector connector = connectorFactory.create(datasource);
        HealthCheckResult healthCheckResult = connector.healthCheck(datasource);
        if (healthCheckResult.success()) {
            datasource.markReachable();
            datasourceRepository.save(datasource);
            log.info("Datasource connection test succeeded: datasourceId={}, latencyMs={}, serverVersion={}",
                    datasource.getId(), healthCheckResult.latencyMs(), healthCheckResult.serverVersion());
            return DatasourceTestConnectionResultDTO.builder()
                    .success(Boolean.TRUE)
                    .latencyMs(healthCheckResult.latencyMs())
                    .message(healthCheckResult.message())
                    .serverVersion(healthCheckResult.serverVersion())
                    .build();
        }
        datasource.markUnreachable();
        datasourceRepository.save(datasource);
        log.warn("Datasource connection test failed: datasourceId={}, latencyMs={}, message={}",
                datasource.getId(), healthCheckResult.latencyMs(), healthCheckResult.message());
        return DatasourceTestConnectionResultDTO.builder()
                .success(Boolean.FALSE)
                .latencyMs(healthCheckResult.latencyMs())
                .message("connection failed: " + healthCheckResult.message())
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
        try {
            return connectorFactory.create(datasource).listTables(datasource);
        } catch (RuntimeException e) {
            String detail = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            throw new BaseBusinessException(ErrorCode.DATASOURCE_CONNECTION_FAILED, "list datasource tables failed", detail, null, false);
        }
    }

    @Override
    public List<FieldSchemaDTO> introspectTableSchema(String datasourceId, String tableName, RequestContextDTO context) {
        permissionChecker.requireRead(context);
        AnalysisDatasource datasource = requireOwnedDatasource(datasourceId, context);
        try {
            Connector connector = connectorFactory.create(datasource);
            QueryCommand command = QueryCommand.builder()
                    .normalizedSql("SELECT * FROM " + tableName + " LIMIT 0")
                    .maxRows(0)
                    .build();
            List<FieldSchemaDTO> fields = connector.inferSchema(datasource, command);

            // 合并 column comment 到 extensions
            Map<String, String> comments = connector.listColumnComments(datasource, tableName);
            if (!comments.isEmpty()) {
                fields = fields.stream().map(f -> {
                    String comment = comments.get(f.getName());
                    if (comment == null || comment.isBlank()) return f;
                    Map<String, Object> ext = new java.util.HashMap<>(f.getExtensions() != null ? f.getExtensions() : Map.of());
                    ext.put("comment", comment);
                    return FieldSchemaDTO.builder()
                            .fieldId(f.getFieldId())
                            .name(f.getName())
                            .path(f.getPath())
                            .valueType(f.getValueType())
                            .nullable(f.getNullable())
                            .displayName(f.getDisplayName())
                            .semanticType(f.getSemanticType())
                            .capabilities(f.getCapabilities())
                            .sampleValues(f.getSampleValues())
                            .stats(f.getStats())
                            .extensions(ext)
                            .build();
                }).toList();
            }
            return fields;
        } catch (RuntimeException e) {
            String detail = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            throw new BaseBusinessException(ErrorCode.DATASOURCE_CONNECTION_FAILED, "introspect table schema failed", detail, null, false);
        }
    }

    @Override
    public List<TableSchemaDTO> introspectAllTableSchemas(String datasourceId, RequestContextDTO context) {
        permissionChecker.requireRead(context);
        AnalysisDatasource datasource = requireOwnedDatasource(datasourceId, context);
        Connector connector = connectorFactory.create(datasource);
        List<String> tables;
        try {
            tables = connector.listTables(datasource);
        } catch (RuntimeException e) {
            String detail = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            throw new BaseBusinessException(ErrorCode.DATASOURCE_CONNECTION_FAILED, "list datasource tables failed", detail, null, false);
        }
        return tables.stream()
                .map(tableName -> {
                    try {
                        QueryCommand command = QueryCommand.builder()
                                .normalizedSql("SELECT * FROM " + tableName + " LIMIT 0")
                                .maxRows(0)
                                .build();
                        List<FieldSchemaDTO> fields = connector.inferSchema(datasource, command);
                        return TableSchemaDTO.builder().tableName(tableName).fields(fields).build();
                    } catch (Exception e) {
                        return TableSchemaDTO.builder().tableName(tableName).fields(List.of()).build();
                    }
                })
                .toList();
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
