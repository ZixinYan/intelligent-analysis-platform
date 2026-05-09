package com.kuaishou.intelligentanalysisplatform.interfaces.rest.v1;

import com.kuaishou.intelligentanalysisplatform.application.DatasourceApplicationService;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.common.response.GlobalExceptionHandler;
import com.kuaishou.intelligentanalysisplatform.common.response.PageResult;
import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceStatus;
import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceType;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceQueryRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceTestConnectionResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
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
        DatasourceApplicationService service = mock(DatasourceApplicationService.class);
        DatasourceDTO created = datasource("ds-1", "demo", "tenant-a");
        DatasourceDTO updated = datasource("ds-1", "demo-updated", "tenant-a");
        DatasourceDTO second = datasource("ds-2", "demo-2", "tenant-a");

        when(service.create(any())).thenAnswer(invocation -> {
            Object request = invocation.getArgument(0);
            String name = (String) request.getClass().getMethod("getName").invoke(request);
            return "demo-2".equals(name) ? second : created;
        });
        when(service.getById(eq("ds-1"), any())).thenReturn(created);
        when(service.getById(eq("missing-id"), any())).thenThrow(new BaseBusinessException(
                ErrorCode.DATASOURCE_NOT_FOUND,
                "datasource not found"
        ));
        when(service.getById(eq("ds-1"), argThat(ctx -> ctx != null && "tenant-b".equals(ctx.getTenantId()))))
                .thenThrow(new BaseBusinessException(ErrorCode.DATASOURCE_ACCESS_DENIED, "datasource access denied"));
        when(service.update(eq("ds-1"), any())).thenReturn(updated);
        when(service.list(any(DatasourceQueryRequestDTO.class))).thenAnswer(invocation -> {
            DatasourceQueryRequestDTO request = invocation.getArgument(0);
            if (request.getType() != null && !request.getType().isBlank()) {
                throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "invalid datasource type");
            }
            return PageResult.<DatasourceDTO>builder()
                    .items(List.of(created))
                    .total(1)
                    .page(1)
                    .pageSize(20)
                    .build();
        });
        doNothing().when(service).delete(eq("ds-1"), any());
        when(service.testConnection(any())).thenReturn(DatasourceTestConnectionResultDTO.builder()
                .success(Boolean.TRUE)
                .latencyMs(12L)
                .message("connection ok")
                .serverVersion("mock")
                .build());
        when(service.listTables(eq("ds-1"), any())).thenReturn(List.of("orders", "users"));

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
    void shouldListDatasourceTables() throws Exception {
        mockMvc.perform(get("/api/v1/datasources/{id}/tables", datasourceId)
                        .header("X-Tenant-Id", "tenant-a")
                        .header("X-User-Id", "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("orders"))
                .andExpect(jsonPath("$.data[1]").value("users"));
    }

    @Test
    void shouldTestConnection() throws Exception {
        mockMvc.perform(post("/api/v1/datasources/{id}/test-connection", datasourceId)
                        .header("X-Tenant-Id", "tenant-a")
                        .header("X-User-Id", "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true));
    }

    private DatasourceDTO datasource(String id, String name, String tenantId) {
        return DatasourceDTO.builder()
                .id(id)
                .tenantId(tenantId)
                .name(name)
                .type(DatasourceType.MYSQL)
                .host("127.0.0.1")
                .port(3306)
                .database("analytics")
                .username("reader")
                .status(DatasourceStatus.ACTIVE)
                .readonly(true)
                .createdAt(1L)
                .updatedAt(1L)
                .createdBy("user-a")
                .build();
    }
}
