package com.kuaishou.intelligentanalysisplatform.interfaces.rest.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.common.response.GlobalExceptionHandler;
import com.kuaishou.intelligentanalysisplatform.infra.observability.QueryMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpsControllerTest {
    private MockMvc mockMvc;
    private QueryMetricsService queryMetricsService;

    @BeforeEach
    void setUp() {
        queryMetricsService = new QueryMetricsService();
        queryMetricsService.updateAsyncQueueDepth(2);
        queryMetricsService.updateDatasourceConcurrency(1);
        mockMvc = MockMvcBuilders.standaloneSetup(new OpsController(queryMetricsService))
                .setControllerAdvice(new GlobalExceptionHandler(new ObjectMapper()))
                .build();
    }

    @Test
    void shouldReturnOpsSummary() throws Exception {
        mockMvc.perform(get("/api/v1/ops/metrics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.asyncQueueDepth").value(2))
                .andExpect(jsonPath("$.data.datasourceConcurrency").value(1));
    }
}
