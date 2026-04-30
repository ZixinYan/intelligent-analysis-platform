package com.kuaishou.intelligentanalysisplatform.interfaces.rest.v1;

import com.kuaishou.intelligentanalysisplatform.common.response.GlobalExceptionHandler;
import com.kuaishou.intelligentanalysisplatform.infra.repository.InMemoryDatasourceRepository;
import com.kuaishou.intelligentanalysisplatform.infra.security.AesGcmCredentialEncryptor;
import com.kuaishou.intelligentanalysisplatform.infra.security.StubPermissionChecker;
import com.kuaishou.intelligentanalysisplatform.infra.stub.StubDatasourceApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DatasourceControllerTest {

    private MockMvc mockMvc;
    private String datasourceId;

    @BeforeEach
    void setUp() throws Exception {
        StubDatasourceApplicationService service = new StubDatasourceApplicationService(
                new InMemoryDatasourceRepository(),
                new AesGcmCredentialEncryptor("0123456789abcdef0123456789abcdef"),
                new StubPermissionChecker()
        );
        mockMvc = MockMvcBuilders.standaloneSetup(new DatasourceController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        MvcResult result = mockMvc.perform(post("/api/v1/datasources")
                        .header("X-Tenant-Id", "tenant-a")
                        .header("X-User-Id", "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"demo\",\"type\":\"MYSQL\",\"host\":\"127.0.0.1\",\"port\":3306,\"database\":\"analytics\",\"username\":\"reader\",\"password\":\"secret\",\"readonly\":true}"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        datasourceId = body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void shouldCreateDatasourceWithoutPasswordInResponse() throws Exception {
        mockMvc.perform(post("/api/v1/datasources")
                        .header("X-Tenant-Id", "tenant-a")
                        .header("X-User-Id", "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"demo-2\",\"type\":\"POSTGRES\",\"host\":\"127.0.0.2\",\"port\":5432,\"database\":\"warehouse\",\"username\":\"reader\",\"password\":\"secret\",\"readonly\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void shouldGetDatasourceById() throws Exception {
        mockMvc.perform(get("/api/v1/datasources/{id}", datasourceId)
                        .header("X-Tenant-Id", "tenant-a")
                        .header("X-User-Id", "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(datasourceId))
                .andExpect(jsonPath("$.data.name").value("demo"));
    }

    @Test
    void shouldUpdateDatasource() throws Exception {
        mockMvc.perform(put("/api/v1/datasources/{id}", datasourceId)
                        .header("X-Tenant-Id", "tenant-a")
                        .header("X-User-Id", "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"demo-updated\",\"readonly\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("demo-updated"));
    }

    @Test
    void shouldListDatasource() throws Exception {
        mockMvc.perform(get("/api/v1/datasources")
                        .header("X-Tenant-Id", "tenant-a")
                        .header("X-User-Id", "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void shouldRejectCrossTenantAccess() throws Exception {
        mockMvc.perform(get("/api/v1/datasources/{id}", datasourceId)
                        .header("X-Tenant-Id", "tenant-b")
                        .header("X-User-Id", "user-b"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DATASOURCE_ACCESS_DENIED"));
    }

    @Test
    void shouldRejectInvalidDatasourceType() throws Exception {
        mockMvc.perform(get("/api/v1/datasources")
                        .param("type", "xxx")
                        .header("X-Tenant-Id", "tenant-a")
                        .header("X-User-Id", "user-a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void shouldReturnNotFoundWhenDatasourceMissing() throws Exception {
        mockMvc.perform(get("/api/v1/datasources/{id}", "missing-id")
                        .header("X-Tenant-Id", "tenant-a")
                        .header("X-User-Id", "user-a"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DATASOURCE_NOT_FOUND"));
    }

    @Test
    void shouldRejectDeleteWithoutUserHeader() throws Exception {
        mockMvc.perform(delete("/api/v1/datasources/{id}", datasourceId)
                        .header("X-Tenant-Id", "tenant-a"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldTestConnection() throws Exception {
        mockMvc.perform(post("/api/v1/datasources/{id}/test-connection", datasourceId)
                        .header("X-Tenant-Id", "tenant-a")
                        .header("X-User-Id", "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true));
    }
}
