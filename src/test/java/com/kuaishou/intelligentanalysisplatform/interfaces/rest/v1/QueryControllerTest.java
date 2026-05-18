package com.kuaishou.intelligentanalysisplatform.interfaces.rest.v1;

import com.kuaishou.intelligentanalysisplatform.application.QueryApplicationService;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.common.response.GlobalExceptionHandler;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.schema.AsyncSubmitResponseDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ValidateResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QueryControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        QueryApplicationService queryApplicationService = mock(QueryApplicationService.class);
        when(queryApplicationService.validate(any())).thenReturn(ValidateResultDTO.builder()
                .queryId("req-validate")
                .valid(true)
                .build());
        when(queryApplicationService.preview(any())).thenReturn(QueryResultDTO.builder()
                .queryId("req-1")
                .status(ExecutionStatus.SUCCEEDED)
                .build());
        when(queryApplicationService.runAsync(any())).thenReturn(AsyncSubmitResponseDTO.builder()
                .taskId("task-req-2")
                .status(ExecutionStatus.QUEUED)
                .pollUrl("/api/v1/query/req-2/status")
                .build());
        when(queryApplicationService.getStatus(eq("req-2"))).thenReturn(QueryResultDTO.builder()
                .queryId("req-2")
                .status(ExecutionStatus.RUNNING)
                .build());
        doNothing().when(queryApplicationService).cancel("req-2");
        when(queryApplicationService.preview(argThat(req -> req != null && req.getSql() != null && req.getSql().isBlank())))
                .thenThrow(new BaseBusinessException(ErrorCode.SQL_PARSE_FAILED, "sql parse failed"));

        mockMvc = MockMvcBuilders.standaloneSetup(new QueryController(queryApplicationService))
                .setControllerAdvice(new GlobalExceptionHandler(new ObjectMapper()))
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
