package com.kuaishou.intelligentanalysisplatform.interfaces.rest.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.application.ai.agent.AiAgentService;
import com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.AiChartOrchestrationService;
import com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.AiSqlOrchestrationService;
import com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.AiWorkflowOrchestrationService;
import com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.sql.AiSseStreamResponder;
import com.kuaishou.intelligentanalysisplatform.common.response.GlobalExceptionHandler;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowDefinitionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowBuildResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowExecuteResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiControllerTest {

    private MockMvc mockMvc;
    private AiWorkflowOrchestrationService aiWorkflowOrchestrationService;

    @BeforeEach
    void setUp() {
        AiSqlOrchestrationService aiSqlOrchestrationService = mock(AiSqlOrchestrationService.class);
        AiChartOrchestrationService aiChartOrchestrationService = mock(AiChartOrchestrationService.class);
        aiWorkflowOrchestrationService = mock(AiWorkflowOrchestrationService.class);
        AiAgentService aiAgentService = mock(AiAgentService.class);
        AiSseStreamResponder sseStreamResponder = mock(AiSseStreamResponder.class);

        when(aiWorkflowOrchestrationService.buildDraft(any(), any())).thenReturn(WorkflowDefinitionDTO.builder()
                .workflowId("wf-legacy")
                .workflowName("legacy")
                .build());
        when(aiWorkflowOrchestrationService.build(any(), any())).thenReturn(AiWorkflowBuildResultDTO.builder()
                .responseType("DRAFT")
                .buildMode("AGENT")
                .workflowId("wf-envelope")
                .saved(Boolean.FALSE)
                .draft(WorkflowDefinitionDTO.builder().workflowId("wf-envelope").workflowName("envelope").build())
                .build());

        mockMvc = MockMvcBuilders.standaloneSetup(new AiController(
                        aiSqlOrchestrationService,
                        aiChartOrchestrationService,
                        aiWorkflowOrchestrationService,
                        aiAgentService,
                        sseStreamResponder))
                .setControllerAdvice(new GlobalExceptionHandler(new ObjectMapper()))
                .build();
    }

    @Test
    void shouldReturnLegacyDraftWhenResponseModeMissing() throws Exception {
        mockMvc.perform(post("/api/v1/ai/workflow/build")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasourceId\":\"ds-1\",\"description\":\"build it\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.workflowId").value("wf-legacy"))
                .andExpect(jsonPath("$.data.workflowName").value("legacy"))
                .andExpect(jsonPath("$.data.responseType").doesNotExist());
    }

    @Test
    void shouldReturnEnvelopeWhenExplicitlyRequested() throws Exception {
        mockMvc.perform(post("/api/v1/ai/workflow/build")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasourceId\":\"ds-1\",\"description\":\"build it\",\"responseMode\":\"ENVELOPE\",\"buildMode\":\"AGENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.responseType").value("DRAFT"))
                .andExpect(jsonPath("$.data.buildMode").value("AGENT"))
                .andExpect(jsonPath("$.data.workflowId").value("wf-envelope"))
                .andExpect(jsonPath("$.data.draft.workflowName").value("envelope"));
    }

    @Test
    void shouldReturnSavedEnvelopeForRunAndSaveMode() throws Exception {
        when(aiWorkflowOrchestrationService.build(any(), any())).thenReturn(AiWorkflowBuildResultDTO.builder()
                .responseType("DRAFT")
                .buildMode("RUN_AND_SAVE")
                .workflowId("wf-saved")
                .datasetId("dataset-1")
                .saved(Boolean.TRUE)
                .execution(AiWorkflowExecuteResultDTO.builder()
                        .supported(true)
                        .status("SUCCEEDED")
                        .workflowId("wf-saved")
                        .finalResultNodeId("node-1")
                        .build())
                .draft(WorkflowDefinitionDTO.builder().workflowId("wf-saved").workflowName("saved draft").build())
                .build());

        mockMvc.perform(post("/api/v1/ai/workflow/build")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasourceId\":\"ds-1\",\"description\":\"build it\",\"responseMode\":\"ENVELOPE\",\"buildMode\":\"RUN_AND_SAVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.responseType").value("DRAFT"))
                .andExpect(jsonPath("$.data.buildMode").value("RUN_AND_SAVE"))
                .andExpect(jsonPath("$.data.saved").value(true))
                .andExpect(jsonPath("$.data.workflowId").value("wf-saved"))
                .andExpect(jsonPath("$.data.datasetId").value("dataset-1"))
                .andExpect(jsonPath("$.data.execution.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.draft.workflowName").value("saved draft"));
    }
}
