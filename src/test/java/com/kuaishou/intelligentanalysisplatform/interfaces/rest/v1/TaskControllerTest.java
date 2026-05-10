package com.kuaishou.intelligentanalysisplatform.interfaces.rest.v1;

import com.kuaishou.intelligentanalysisplatform.application.TaskApplicationService;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.common.response.GlobalExceptionHandler;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.schema.AsyncTaskStatusDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TaskApplicationService taskApplicationService = mock(TaskApplicationService.class);
        when(taskApplicationService.getTask("task-demo")).thenReturn(AsyncTaskStatusDTO.builder()
                .taskId("task-demo")
                .status(ExecutionStatus.RUNNING)
                .build());
        when(taskApplicationService.getTask("missing")).thenThrow(new BaseBusinessException(
                ErrorCode.ASYNC_TASK_NOT_FOUND,
                "task not found"
        ));
        doNothing().when(taskApplicationService).cancelTask("task-demo");

        mockMvc = MockMvcBuilders.standaloneSetup(new TaskController(taskApplicationService))
                .setControllerAdvice(new GlobalExceptionHandler(new ObjectMapper()))
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
