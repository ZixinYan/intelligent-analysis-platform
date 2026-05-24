package com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.impl;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.application.DatasetApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.WorkflowApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.workflow.AiWorkflowDraftBuilder;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SaveDatasetRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SavedDatasetSummaryDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowDefinitionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowRunResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowSaveRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowBuildRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowBuildResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.SyncExecutionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DefaultAiWorkflowOrchestrationServiceTest {

    private static DefaultAiWorkflowOrchestrationService service(AiWorkflowDraftBuilder draftBuilder,
                                                                  WorkflowApplicationService wfService,
                                                                  SyncExecutionService syncService,
                                                                  DatasetApplicationService dsService) {
        return new DefaultAiWorkflowOrchestrationService(draftBuilder, wfService, syncService, dsService);
    }

    @Test
    void shouldDelegateBuildDraftToDraftBuilder() {
        AiWorkflowDraftBuilder draftBuilder = mock(AiWorkflowDraftBuilder.class);
        WorkflowApplicationService wfService = mock(WorkflowApplicationService.class);
        SyncExecutionService syncService = mock(SyncExecutionService.class);
        DatasetApplicationService dsService = mock(DatasetApplicationService.class);
        DefaultAiWorkflowOrchestrationService svc = service(draftBuilder, wfService, syncService, dsService);

        AiWorkflowBuildRequestDTO request = new AiWorkflowBuildRequestDTO();
        request.setDatasourceId("ds-1");
        request.setDescription("build");
        RequestContextDTO context = RequestContextDTO.builder().tenantId("t").userId("u").requestId("r").build();
        WorkflowDefinitionDTO draft = WorkflowDefinitionDTO.builder().workflowId("wf-1").workflowName("draft").build();

        when(draftBuilder.buildDraft(request, context)).thenReturn(draft);

        WorkflowDefinitionDTO result = svc.buildDraft(request, context);

        assertSame(draft, result);
        verify(draftBuilder).buildDraft(request, context);
        verifyNoInteractions(wfService, syncService, dsService);
    }

    @Test
    void shouldWrapDraftWithLegacyCompatibleEnvelope() {
        AiWorkflowDraftBuilder draftBuilder = mock(AiWorkflowDraftBuilder.class);
        WorkflowApplicationService wfService = mock(WorkflowApplicationService.class);
        SyncExecutionService syncService = mock(SyncExecutionService.class);
        DatasetApplicationService dsService = mock(DatasetApplicationService.class);
        DefaultAiWorkflowOrchestrationService svc = service(draftBuilder, wfService, syncService, dsService);

        AiWorkflowBuildRequestDTO request = new AiWorkflowBuildRequestDTO();
        request.setDatasourceId("ds-1");
        request.setDescription("build");
        request.setConversationId("conv-1");
        RequestContextDTO context = RequestContextDTO.builder().tenantId("t").userId("u").requestId("r").build();
        WorkflowDefinitionDTO draft = WorkflowDefinitionDTO.builder().workflowId("wf-1").workflowName("draft").build();

        when(draftBuilder.buildDraft(request, context)).thenReturn(draft);

        AiWorkflowBuildResultDTO result = svc.build(request, context);

        assertEquals("DRAFT", result.getResponseType());
        assertEquals("DRAFT_ONLY", result.getBuildMode());
        assertSame(draft, result.getDraft());
        assertEquals("wf-1", result.getWorkflowId());
        assertFalse(result.getSaved());
        assertNull(result.getDatasetId());
        assertNull(result.getExecution());
        assertEquals(List.of(), result.getClarifications());
        assertEquals(Boolean.TRUE, result.getMetadata().get("legacyCompatible"));
        assertEquals(Boolean.FALSE, result.getMetadata().get("runAndSaveRequested"));
        assertEquals(Boolean.FALSE, result.getMetadata().get("agentEntry"));
        assertEquals("conv-1", result.getMetadata().get("conversationId"));
        verifyNoInteractions(wfService, syncService, dsService);
    }

    @Test
    void shouldSaveDraftExecuteWorkflowAndPersistDatasetWhenRunAndSaveRequested() {
        AiWorkflowDraftBuilder draftBuilder = mock(AiWorkflowDraftBuilder.class);
        WorkflowApplicationService wfService = mock(WorkflowApplicationService.class);
        SyncExecutionService syncService = mock(SyncExecutionService.class);
        DatasetApplicationService dsService = mock(DatasetApplicationService.class);
        DefaultAiWorkflowOrchestrationService svc = service(draftBuilder, wfService, syncService, dsService);

        AiWorkflowBuildRequestDTO request = new AiWorkflowBuildRequestDTO();
        request.setDatasourceId("ds-1");
        request.setDescription("build");
        request.setWorkflowName("requested name");
        request.setRunAndSave(Boolean.TRUE);
        RequestContextDTO context = RequestContextDTO.builder().tenantId("t").userId("u").requestId("r").build();

        WorkflowDefinitionDTO draft = WorkflowDefinitionDTO.builder()
                .workflowId("draft-id").workflowName("draft-name")
                .nodes(List.of()).edges(List.of()).positions(Map.of()).build();
        WorkflowDefinitionDTO savedWorkflow = WorkflowDefinitionDTO.builder()
                .workflowId("saved-id").workflowName("saved-name")
                .nodes(List.of()).edges(List.of()).positions(Map.of()).build();
        DatasetDTO dataset = DatasetDTO.builder().rows(List.of(Map.of("a", 1))).build();

        when(draftBuilder.buildDraft(request, context)).thenReturn(draft);
        when(wfService.update(eq("draft-id"), any())).thenReturn(savedWorkflow);
        when(wfService.getById(eq("saved-id"), any())).thenReturn(savedWorkflow);
        when(syncService.runWorkflow(any())).thenReturn(WorkflowRunResultDTO.builder()
                .status(ExecutionStatus.SUCCEEDED)
                .finalResult(StandardResultDTO.builder().dataset(dataset).build())
                .finalResultNodeId("node-1")
                .build());
        when(dsService.save(any(), eq("t"), eq("u"))).thenReturn(
                SavedDatasetSummaryDTO.builder().datasetId("dataset-1").build());

        AiWorkflowBuildResultDTO result = svc.build(request, context);

        ArgumentCaptor<String> saveIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<WorkflowSaveRequestDTO> saveCaptor = ArgumentCaptor.forClass(WorkflowSaveRequestDTO.class);
        ArgumentCaptor<SaveDatasetRequestDTO> datasetCaptor = ArgumentCaptor.forClass(SaveDatasetRequestDTO.class);

        verify(wfService).update(saveIdCaptor.capture(), saveCaptor.capture());
        verify(syncService).runWorkflow(any());
        verify(dsService).save(datasetCaptor.capture(), eq("t"), eq("u"));

        assertEquals("draft-id", saveIdCaptor.getValue());
        assertEquals("requested name", saveCaptor.getValue().getWorkflowName());
        assertEquals("requested name-结果", datasetCaptor.getValue().getName());
        assertEquals("saved-id", datasetCaptor.getValue().getSourceWorkflowId());
        assertEquals("node-1", datasetCaptor.getValue().getSourceNodeId());

        assertTrue(result.getSaved());
        assertEquals("RUN_AND_SAVE", result.getBuildMode());
        assertEquals("saved-id", result.getWorkflowId());
        assertEquals("dataset-1", result.getDatasetId());
        assertSame(savedWorkflow, result.getDraft());
        assertEquals("dataset-1", result.getExecution().getDatasetId());
        assertEquals(Boolean.TRUE, result.getMetadata().get("runAndSaveRequested"));
    }

    @Test
    void shouldNotPersistDatasetWhenFinalResultMissing() {
        AiWorkflowDraftBuilder draftBuilder = mock(AiWorkflowDraftBuilder.class);
        WorkflowApplicationService wfService = mock(WorkflowApplicationService.class);
        SyncExecutionService syncService = mock(SyncExecutionService.class);
        DatasetApplicationService dsService = mock(DatasetApplicationService.class);
        DefaultAiWorkflowOrchestrationService svc = service(draftBuilder, wfService, syncService, dsService);

        AiWorkflowBuildRequestDTO request = new AiWorkflowBuildRequestDTO();
        request.setDatasourceId("ds-1");
        request.setDescription("build");
        request.setRunAndSave(Boolean.TRUE);
        RequestContextDTO context = RequestContextDTO.builder().tenantId("t").userId("u").requestId("r").build();

        WorkflowDefinitionDTO draft = WorkflowDefinitionDTO.builder()
                .workflowId("draft-id").workflowName("draft-name")
                .nodes(List.of()).edges(List.of()).positions(Map.of()).build();
        WorkflowDefinitionDTO savedWorkflow = WorkflowDefinitionDTO.builder()
                .workflowId("saved-id").workflowName("saved-name")
                .nodes(List.of()).edges(List.of()).positions(Map.of()).build();

        when(draftBuilder.buildDraft(request, context)).thenReturn(draft);
        when(wfService.update(eq("draft-id"), any())).thenReturn(savedWorkflow);
        when(wfService.getById(eq("saved-id"), any())).thenReturn(savedWorkflow);
        when(syncService.runWorkflow(any())).thenReturn(WorkflowRunResultDTO.builder()
                .status(ExecutionStatus.SUCCEEDED).build());

        AiWorkflowBuildResultDTO result = svc.build(request, context);

        assertTrue(result.getSaved());
        assertNull(result.getDatasetId());
        verify(dsService, never()).save(any(), any(), any());
    }

    @Test
    void shouldFallbackToBuiltDraftWhenSaveResultHasNoWorkflow() {
        AiWorkflowDraftBuilder draftBuilder = mock(AiWorkflowDraftBuilder.class);
        WorkflowApplicationService wfService = mock(WorkflowApplicationService.class);
        SyncExecutionService syncService = mock(SyncExecutionService.class);
        DatasetApplicationService dsService = mock(DatasetApplicationService.class);
        DefaultAiWorkflowOrchestrationService svc = service(draftBuilder, wfService, syncService, dsService);

        AiWorkflowBuildRequestDTO request = new AiWorkflowBuildRequestDTO();
        request.setDatasourceId("ds-1");
        request.setDescription("build");
        request.setRunAndSave(Boolean.TRUE);
        RequestContextDTO context = RequestContextDTO.builder().tenantId("t").userId("u").requestId("r").build();

        WorkflowDefinitionDTO draft = WorkflowDefinitionDTO.builder()
                .workflowId("draft-id").workflowName("draft-name")
                .nodes(List.of()).edges(List.of()).positions(Map.of()).build();
        // save returns a workflow with no graph (simulates null workflow in old save result)
        WorkflowDefinitionDTO savedWorkflowNoGraph = WorkflowDefinitionDTO.builder()
                .workflowId("saved-id").workflowName("saved-name").build();

        when(draftBuilder.buildDraft(request, context)).thenReturn(draft);
        when(wfService.update(eq("draft-id"), any())).thenReturn(savedWorkflowNoGraph);
        when(wfService.getById(eq("saved-id"), any())).thenReturn(savedWorkflowNoGraph);
        when(syncService.runWorkflow(any())).thenReturn(WorkflowRunResultDTO.builder()
                .status(ExecutionStatus.FAILED).workflowId("saved-id").build());

        AiWorkflowBuildResultDTO result = svc.build(request, context);

        assertTrue(result.getSaved());
        assertEquals("saved-id", result.getWorkflowId());
        assertEquals("RUN_AND_SAVE", result.getBuildMode());
        assertNull(result.getDatasetId());
    }

    @Test
    void shouldPreferRunAndSaveBuildMode() {
        AiWorkflowDraftBuilder draftBuilder = mock(AiWorkflowDraftBuilder.class);
        WorkflowApplicationService wfService = mock(WorkflowApplicationService.class);
        SyncExecutionService syncService = mock(SyncExecutionService.class);
        DatasetApplicationService dsService = mock(DatasetApplicationService.class);
        DefaultAiWorkflowOrchestrationService svc = service(draftBuilder, wfService, syncService, dsService);

        AiWorkflowBuildRequestDTO request = new AiWorkflowBuildRequestDTO();
        request.setDatasourceId("ds-1");
        request.setDescription("build");
        request.setBuildMode("RUN_AND_SAVE");
        RequestContextDTO context = RequestContextDTO.builder().tenantId("t").userId("u").requestId("r").build();

        WorkflowDefinitionDTO draft = WorkflowDefinitionDTO.builder()
                .workflowId("wf-2").workflowName("draft")
                .nodes(List.of()).edges(List.of()).positions(Map.of()).build();

        when(draftBuilder.buildDraft(request, context)).thenReturn(draft);
        when(wfService.update(eq("wf-2"), any())).thenReturn(draft);
        when(wfService.getById(eq("wf-2"), any())).thenReturn(draft);
        when(syncService.runWorkflow(any())).thenReturn(WorkflowRunResultDTO.builder()
                .status(ExecutionStatus.FAILED).workflowId("wf-2").build());

        AiWorkflowBuildResultDTO result = svc.build(request, context);

        assertEquals("RUN_AND_SAVE", result.getBuildMode());
        assertTrue(result.getSaved());
        verify(wfService).update(eq("wf-2"), any());
        verify(syncService).runWorkflow(any());
    }

    @Test
    void shouldMarkAgentModeWhenRequested() {
        AiWorkflowDraftBuilder draftBuilder = mock(AiWorkflowDraftBuilder.class);
        WorkflowApplicationService wfService = mock(WorkflowApplicationService.class);
        SyncExecutionService syncService = mock(SyncExecutionService.class);
        DatasetApplicationService dsService = mock(DatasetApplicationService.class);
        DefaultAiWorkflowOrchestrationService svc = service(draftBuilder, wfService, syncService, dsService);

        AiWorkflowBuildRequestDTO request = new AiWorkflowBuildRequestDTO();
        request.setDatasourceId("ds-1");
        request.setDescription("build");
        request.setBuildMode("agent");
        request.setAgentTaskId("task-1");
        RequestContextDTO context = RequestContextDTO.builder().tenantId("t").userId("u").requestId("r").build();
        WorkflowDefinitionDTO draft = WorkflowDefinitionDTO.builder().workflowId("wf-2").workflowName("draft").build();

        when(draftBuilder.buildDraft(request, context)).thenReturn(draft);

        AiWorkflowBuildResultDTO result = svc.build(request, context);

        assertEquals("AGENT", result.getBuildMode());
        assertEquals("task-1", result.getAgentTaskId());
        assertTrue((Boolean) result.getMetadata().get("agentEntry"));
        verifyNoInteractions(wfService, syncService, dsService);
    }
}
