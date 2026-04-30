package com.kuaishou.intelligentanalysisplatform.infra.stub;

import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceType;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceCreateRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceQueryAccessDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceQueryRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceTestConnectionRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceUpdateRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.infra.repository.InMemoryDatasourceRepository;
import com.kuaishou.intelligentanalysisplatform.infra.security.AesGcmCredentialEncryptor;
import com.kuaishou.intelligentanalysisplatform.infra.security.StubPermissionChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StubDatasourceApplicationServiceTest {

    private StubDatasourceApplicationService service;
    private RequestContextDTO context;

    @BeforeEach
    void setUp() {
        service = new StubDatasourceApplicationService(
                new InMemoryDatasourceRepository(),
                new AesGcmCredentialEncryptor("0123456789abcdef0123456789abcdef"),
                new StubPermissionChecker()
        );
        context = RequestContextDTO.builder().tenantId("tenant-a").userId("user-a").build();
    }

    @Test
    void shouldEncryptPasswordAndReturnQueryAccess() {
        var created = service.create(DatasourceCreateRequestDTO.builder()
                .name("demo")
                .type(DatasourceType.MYSQL)
                .host("127.0.0.1")
                .port(3306)
                .database("analytics")
                .username("reader")
                .password("secret")
                .readonly(true)
                .context(context)
                .build());

        DatasourceQueryAccessDTO access = service.getQueryAccess(created.getId(), context);
        assertNotEquals("secret", access.getEncryptedPassword());
        assertEquals("reader", access.getUsername());
    }

    @Test
    void shouldKeepPasswordWhenUpdateWithoutPassword() {
        var created = service.create(DatasourceCreateRequestDTO.builder()
                .name("demo")
                .type(DatasourceType.MYSQL)
                .host("127.0.0.1")
                .port(3306)
                .database("analytics")
                .username("reader")
                .password("secret")
                .readonly(true)
                .context(context)
                .build());
        String encrypted = service.getQueryAccess(created.getId(), context).getEncryptedPassword();

        service.update(created.getId(), DatasourceUpdateRequestDTO.builder()
                .name("demo2")
                .readonly(true)
                .context(context)
                .build());

        assertEquals(encrypted, service.getQueryAccess(created.getId(), context).getEncryptedPassword());
    }

    @Test
    void shouldRejectNonReadonlyDatasource() {
        assertThrows(BaseBusinessException.class, () -> service.create(DatasourceCreateRequestDTO.builder()
                .name("demo")
                .type(DatasourceType.POSTGRES)
                .host("127.0.0.1")
                .port(5432)
                .database("analytics")
                .username("reader")
                .password("secret")
                .readonly(false)
                .context(context)
                .build()));
    }

    @Test
    void shouldListDatasourceByTenant() {
        service.create(DatasourceCreateRequestDTO.builder()
                .name("demo")
                .type(DatasourceType.MYSQL)
                .host("127.0.0.1")
                .port(3306)
                .database("analytics")
                .username("reader")
                .password("secret")
                .readonly(true)
                .context(context)
                .build());

        var page = service.list(DatasourceQueryRequestDTO.builder().context(context).build());
        assertEquals(1, page.getTotal());
    }

    @Test
    void shouldFailConnectionWhenHostContainsFail() {
        var created = service.create(DatasourceCreateRequestDTO.builder()
                .name("demo")
                .type(DatasourceType.MYSQL)
                .host("fail-host")
                .port(3306)
                .database("analytics")
                .username("reader")
                .password("secret")
                .readonly(true)
                .context(context)
                .build());

        BaseBusinessException ex = assertThrows(BaseBusinessException.class,
                () -> service.testConnection(DatasourceTestConnectionRequestDTO.builder()
                        .datasourceId(created.getId())
                        .context(context)
                        .build()));
        assertEquals("DATASOURCE_CONNECTION_FAILED", ex.getErrorCode().getCode());
        assertEquals("UNREACHABLE", service.getById(created.getId(), context).getStatus().name());
    }

    @Test
    void shouldSucceedConnectionWhenDatasourceValid() {
        var created = service.create(DatasourceCreateRequestDTO.builder()
                .name("demo")
                .type(DatasourceType.POSTGRES)
                .host("127.0.0.1")
                .port(5432)
                .database("warehouse")
                .username("reader")
                .password("secret")
                .readonly(true)
                .context(context)
                .build());

        var result = service.testConnection(DatasourceTestConnectionRequestDTO.builder()
                .datasourceId(created.getId())
                .context(context)
                .build());
        assertTrue(result.getSuccess());
    }

    @Test
    void shouldRejectGetQueryAccessWithoutUser() {
        var created = service.create(DatasourceCreateRequestDTO.builder()
                .name("demo")
                .type(DatasourceType.MYSQL)
                .host("127.0.0.1")
                .port(3306)
                .database("analytics")
                .username("reader")
                .password("secret")
                .readonly(true)
                .context(context)
                .build());

        RequestContextDTO noUser = RequestContextDTO.builder().tenantId("tenant-a").build();
        assertThrows(BaseBusinessException.class, () -> service.getQueryAccess(created.getId(), noUser));
    }
}
