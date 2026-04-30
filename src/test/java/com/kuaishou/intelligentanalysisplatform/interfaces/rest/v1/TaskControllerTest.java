package com.kuaishou.intelligentanalysisplatform.interfaces.rest.v1;

import com.kuaishou.intelligentanalysisplatform.common.response.GlobalExceptionHandler;
import com.kuaishou.intelligentanalysisplatform.infra.stub.StubTaskApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TaskController(new StubTaskApplicationService()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldGetTaskStatus() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/task-demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.status").value("RUNNING"));
    }

    @Test
    void shouldCancelTask() throws Exception {
        mockMvc.perform(post("/api/v1/tasks/task-demo/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void shouldReturnNotFoundWhenTaskMissing() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/missing").header("X-Trace-Id", "trace-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ASYNC_TASK_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").value("trace-1"))
                .andExpect(jsonPath("$.data.requestId").value("GET /api/v1/tasks/missing"));
    }
}
