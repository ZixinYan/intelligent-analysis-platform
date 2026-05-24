package com.kuaishou.intelligentanalysisplatform.application.impl;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceStatus;
import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceType;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceCreateRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceQueryAccessDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceQueryRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceUpdateRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.Connector;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.ConnectorFactory;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.HealthCheckResult;
import com.kuaishou.intelligentanalysisplatform.infra.connector.pool.HikariPoolRegistry;
import com.kuaishou.intelligentanalysisplatform.infra.repository.JdbcDatasourceRepository;
import com.kuaishou.intelligentanalysisplatform.infra.security.AesGcmCredentialEncryptor;
import com.kuaishou.intelligentanalysisplatform.infra.security.DefaultPermissionChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@JdbcTest
@Import({DatasourceApplicationServiceImpl.class, JdbcDatasourceRepository.class, ObjectMapper.class, AesGcmCredentialEncryptor.class, DefaultPermissionChecker.class, DatasourceApplicationServiceTest.TestConfig.class})
@Sql("classpath:schema.sql")
@TestPropertySource(properties = "datasource.credential.secret=0123456789abcdef0123456789abcdef")
class DatasourceApplicationServiceTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        Connector connector() {
            return mock(Connector.class);
        }

        @Bean
        ConnectorFactory connectorFactory(Connector connector) {
            ConnectorFactory factory = mock(ConnectorFactory.class);
            when(factory.create(any())).thenReturn(connector);
            return factory;
        }

        @Bean
        HikariPoolRegistry hikariPoolRegistry() {
            HikariPoolRegistry registry = mock(HikariPoolRegistry.class);
            doNothing().when(registry).evict(any());
            return registry;
        }
    }

    @Autowired
    private DatasourceApplicationServiceImpl service;

    @Autowired
    private Connector connector;

    private RequestContextDTO context;

    @BeforeEach
    void setUp() {
        context = RequestContextDTO.builder().tenantId("tenant-a").userId("user-a").build();
        reset(connector);
        when(connector.listTables(any())).thenReturn(List.of("orders", "users"));
        when(connector.healthCheck(any())).thenReturn(new HealthCheckResult(true, 12L, "8.0.36", "connection ok"));
    }

    @Test
    void shouldEncryptPasswordAndReturnQueryAccess() {
        var created = service.create(createRequest("demo", DatasourceType.MYSQL));

        DatasourceQueryAccessDTO access = service.getQueryAccess(created.getId(), context);
        assertNotEquals("secret", access.getEncryptedPassword());
        assertEquals("reader", access.getUsername());
    }

    @Test
    void shouldKeepPasswordWhenUpdateWithoutPassword() {
        var created = service.create(createRequest("demo", DatasourceType.MYSQL));
        String encrypted = service.getQueryAccess(created.getId(), context).getEncryptedPassword();

        service.update(created.getId(), DatasourceUpdateRequestDTO.builder()
                .name("demo2")
                .type(DatasourceType.MYSQL)
                .host("127.0.0.1")
                .port(3306)
                .database("analytics")
                .username("reader")
                .readonly(true)
                .context(context)
                .build());

        assertEquals(encrypted, service.getQueryAccess(created.getId(), context).getEncryptedPassword());
    }

    @Test
    void shouldListDatasourceByTenant() {
        service.create(createRequest("demo", DatasourceType.MYSQL));

        var page = service.list(DatasourceQueryRequestDTO.builder().context(context).build());
        assertEquals(1, page.getTotal());
    }

    @Test
    void shouldRejectGetQueryAccessWithoutUser() {
        var created = service.create(createRequest("demo", DatasourceType.MYSQL));

        RequestContextDTO noUser = RequestContextDTO.builder().tenantId("tenant-a").build();
        assertThrows(BaseBusinessException.class, () -> service.getQueryAccess(created.getId(), noUser));
    }

    @Test
    void shouldListTablesThroughConnectorFactory() {
        var created = service.create(createRequest("demo", DatasourceType.MYSQL));

        assertEquals(List.of("orders", "users"), service.listTables(created.getId(), context));
    }

    @Test
    void shouldMarkDatasourceReachableWhenConnectionSucceeds() {
        var created = service.create(createRequest("demo", DatasourceType.MYSQL));

        var result = service.testConnection(com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceTestConnectionRequestDTO.builder()
                .datasourceId(created.getId())
                .context(context)
                .build());

        assertEquals(true, result.getSuccess());
        assertEquals("8.0.36", result.getServerVersion());
        assertEquals(DatasourceStatus.ACTIVE, service.getById(created.getId(), context).getStatus());
    }

    @Test
    void shouldMarkDatasourceUnreachableWhenHealthCheckFails() {
        var created = service.create(createRequest("demo-fail", DatasourceType.MYSQL));
        when(connector.healthCheck(any())).thenReturn(new HealthCheckResult(false, 23L, null, "timeout"));

        var result = service.testConnection(com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceTestConnectionRequestDTO.builder()
                .datasourceId(created.getId())
                .context(context)
                .build());

        assertFalse(result.getSuccess());
        assertEquals("connection failed: timeout", result.getMessage());
        assertEquals(DatasourceStatus.UNREACHABLE, service.getById(created.getId(), context).getStatus());
    }

    private DatasourceCreateRequestDTO createRequest(String name, DatasourceType type) {
        return DatasourceCreateRequestDTO.builder()
                .name(name)
                .type(type)
                .host("127.0.0.1")
                .port(3306)
                .database("analytics")
                .username("reader")
                .password("secret")
                .readonly(true)
                .context(context)
                .build();
    }
}
