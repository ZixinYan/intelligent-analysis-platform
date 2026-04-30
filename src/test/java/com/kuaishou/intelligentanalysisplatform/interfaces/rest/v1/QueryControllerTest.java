package com.kuaishou.intelligentanalysisplatform.interfaces.rest.v1;

import com.kuaishou.intelligentanalysisplatform.common.response.GlobalExceptionHandler;
import com.kuaishou.intelligentanalysisplatform.infra.query.guard.RuleBasedSqlGuard;
import com.kuaishou.intelligentanalysisplatform.infra.stub.StubAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QueryControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new QueryController(new StubAnalysisService(new RuleBasedSqlGuard())))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldValidateQuery() throws Exception {
        mockMvc.perform(post("/api/v1/query/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"req-validate\",\"datasourceId\":\"ds-1\",\"sql\":\"select 1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.queryId").value("req-validate"))
                .andExpect(jsonPath("$.data.valid").value(true));
    }

    @Test
    void shouldPreviewQuery() throws Exception {
        mockMvc.perform(post("/api/v1/query/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"req-1\",\"datasourceId\":\"ds-1\",\"sql\":\"select 1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.queryId").value("req-1"));
    }

    @Test
    void shouldRejectBlankSql() throws Exception {
        mockMvc.perform(post("/api/v1/query/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"req-blank\",\"datasourceId\":\"ds-1\",\"sql\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SQL_PARSE_FAILED"));
    }

    @Test
    void shouldSubmitAsyncQuery() throws Exception {
        mockMvc.perform(post("/api/v1/query/run-async")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"req-2\",\"datasourceId\":\"ds-1\",\"sql\":\"select 1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value("task-req-2"))
                .andExpect(jsonPath("$.data.pollUrl").value("/api/v1/query/req-2/status"));
    }

    @Test
    void shouldReturnQueryStatus() throws Exception {
        mockMvc.perform(get("/api/v1/query/req-2/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.queryId").value("req-2"))
                .andExpect(jsonPath("$.data.status").value("RUNNING"));
    }

    @Test
    void shouldCancelQuery() throws Exception {
        mockMvc.perform(delete("/api/v1/query/req-2/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }
}
