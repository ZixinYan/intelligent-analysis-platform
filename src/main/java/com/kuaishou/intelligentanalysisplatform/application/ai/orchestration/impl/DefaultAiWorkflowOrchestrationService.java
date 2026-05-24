package com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.impl;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.application.DatasetApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.WorkflowApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.AiWorkflowOrchestrationService;
import com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.workflow.AiWorkflowDraftBuilder;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SaveDatasetRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SavedDatasetSummaryDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowDefinitionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowRunRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowSaveRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiDatasetSaveResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowBuildRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowBuildResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowDryRunRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowDryRunResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowExecuteRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowExecuteResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowLoadResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowSaveRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowSaveResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.SyncExecutionService;
import org.springframework.stereotype.Service;

@Service
public class DefaultAiWorkflowOrchestrationService implements AiWorkflowOrchestrationService {

    private static final String BUILD_MODE_DRAFT_ONLY = "DRAFT_ONLY";
    private static final String BUILD_MODE_RUN_AND_SAVE = "RUN_AND_SAVE";
    private static final String BUILD_MODE_AGENT = "AGENT";
    private static final String RESPONSE_TYPE_DRAFT = "DRAFT";
    private static final String DEFAULT_WORKFLOW_NAME = "AI生成工作流";
    private static final String DEFAULT_DATASET_NAME = "AI生成结果数据集";
    private static final String STATUS_READY = "READY";

    private final AiWorkflowDraftBuilder aiWorkflowDraftBuilder;
    private final WorkflowApplicationService workflowApplicationService;
    private final SyncExecutionService syncExecutionService;
    private final DatasetApplicationService datasetApplicationService;

    public DefaultAiWorkflowOrchestrationService(AiWorkflowDraftBuilder aiWorkflowDraftBuilder,
                                                 WorkflowApplicationService workflowApplicationService,
                                                 SyncExecutionService syncExecutionService,
                                                 DatasetApplicationService datasetApplicationService) {
        this.aiWorkflowDraftBuilder = aiWorkflowDraftBuilder;
        this.workflowApplicationService = workflowApplicationService;
        this.syncExecutionService = syncExecutionService;
        this.datasetApplicationService = datasetApplicationService;
    }

    @Override
    public WorkflowDefinitionDTO buildDraft(AiWorkflowBuildRequestDTO request, RequestContextDTO context) {
        return aiWorkflowDraftBuilder.buildDraft(request, context);
    }

    @Override
    public AiWorkflowBuildResultDTO build(AiWorkflowBuildRequestDTO request, RequestContextDTO context) {
        WorkflowDefinitionDTO draft = buildDraft(request, context);
        String buildMode = normalizeBuildMode(request);
        if (BUILD_MODE_RUN_AND_SAVE.equals(buildMode)) {
            AiWorkflowSaveResultDTO saveResult = saveDraft(toSaveRequest(request, draft), context);
            WorkflowDefinitionDTO finalDraft = saveResult.getWorkflow() != null ? saveResult.getWorkflow() : draft;
            AiWorkflowExecuteResultDTO execution = executeWorkflow(AiWorkflowExecuteRequestDTO.builder()
                    .workflowId(saveResult.getWorkflowId())
                    .inputs(Map.of())
                    .build(), context);
            AiDatasetSaveResultDTO datasetSaveResult = saveFinalResult(request, saveResult, execution, context);
            if (datasetSaveResult != null) {
                execution.setDatasetId(datasetSaveResult.getDatasetId());
            }
            return AiWorkflowBuildResultDTO.builder()
                    .responseType(RESPONSE_TYPE_DRAFT)
                    .buildMode(buildMode)
                    .draft(finalDraft)
                    .agentTaskId(request.getAgentTaskId())
                    .saved(Boolean.TRUE)
                    .workflowId(saveResult.getWorkflowId())
                    .datasetId(datasetSaveResult == null ? null : datasetSaveResult.getDatasetId())
                    .execution(execution)
                    .clarifications(List.of())
                    .metadata(buildMetadata(request, buildMode))
                    .build();
        }
        return AiWorkflowBuildResultDTO.builder()
                .responseType(RESPONSE_TYPE_DRAFT)
                .buildMode(buildMode)
                .draft(draft)
                .agentTaskId(request.getAgentTaskId())
                .saved(Boolean.FALSE)
                .workflowId(draft.getWorkflowId())
                .datasetId(null)
                .execution(null)
                .clarifications(List.of())
                .metadata(buildMetadata(request, buildMode))
                .build();
    }

    @Override
    public AiWorkflowSaveResultDTO saveDraft(AiWorkflowSaveRequestDTO request, RequestContextDTO context) {
        boolean hasId = request != null && request.getWorkflowId() != null && !request.getWorkflowId().isBlank();
        WorkflowSaveRequestDTO saveRequest = WorkflowSaveRequestDTO.builder()
                .workflowName(request.getWorkflowName())
                .nodes(request.getNodes())
                .edges(request.getEdges())
                .positions(request.getPositions())
                .context(context)
                .build();
        WorkflowDefinitionDTO workflow = hasId
                ? workflowApplicationService.update(request.getWorkflowId().trim(), saveRequest)
                : workflowApplicationService.create(saveRequest);
        return AiWorkflowSaveResultDTO.builder()
                .workflowId(workflow.getWorkflowId())
                .workflowName(workflow.getWorkflowName())
                .workflow(workflow)
                .created(!hasId)
                .build();
    }

    @Override
    public AiWorkflowLoadResultDTO loadWorkflow(String workflowId, RequestContextDTO context) {
        WorkflowDefinitionDTO workflow = workflowApplicationService.getById(workflowId, context);
        return AiWorkflowLoadResultDTO.builder()
                .workflowId(workflow.getWorkflowId())
                .workflow(workflow)
                .build();
    }

    @Override
    public AiWorkflowExecuteResultDTO executeWorkflow(AiWorkflowExecuteRequestDTO request, RequestContextDTO context) {
        WorkflowDefinitionDTO workflow = workflowApplicationService.getById(request.getWorkflowId(), context);
        com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowRunResultDTO runResult =
                syncExecutionService.runWorkflow(WorkflowRunRequestDTO.builder()
                        .workflowId(workflow.getWorkflowId())
                        .inputs(request.getInputs() == null ? Map.of() : request.getInputs())
                        .nodes(workflow.getNodes())
                        .edges(workflow.getEdges())
                        .context(context)
                        .async(Boolean.FALSE)
                        .build());
        return AiWorkflowExecuteResultDTO.builder()
                .supported(true)
                .status(runResult.getStatus() == null ? null : runResult.getStatus().name())
                .workflowId(workflow.getWorkflowId())
                .finalResult(runResult.getFinalResult())
                .finalResultNodeId(runResult.getFinalResultNodeId())
                .build();
    }

    @Override
    public AiWorkflowDryRunResultDTO dryRunWorkflow(AiWorkflowDryRunRequestDTO request, RequestContextDTO context) {
        WorkflowDefinitionDTO workflow = workflowApplicationService.getById(request.getWorkflowId(), context);
        return AiWorkflowDryRunResultDTO.builder()
                .supported(true)
                .status(STATUS_READY)
                .message("workflow is loadable")
                .workflowId(workflow.getWorkflowId())
                .warnings(List.of())
                .build();
    }

    private String normalizeBuildMode(AiWorkflowBuildRequestDTO request) {
        if (BUILD_MODE_RUN_AND_SAVE.equalsIgnoreCase(request.getBuildMode()) || Boolean.TRUE.equals(request.getRunAndSave())) {
            return BUILD_MODE_RUN_AND_SAVE;
        }
        if (BUILD_MODE_AGENT.equalsIgnoreCase(request.getBuildMode())) {
            return BUILD_MODE_AGENT;
        }
        return BUILD_MODE_DRAFT_ONLY;
    }

    private AiWorkflowSaveRequestDTO toSaveRequest(AiWorkflowBuildRequestDTO request, WorkflowDefinitionDTO draft) {
        return AiWorkflowSaveRequestDTO.builder()
                .workflowId(draft.getWorkflowId())
                .workflowName(resolveWorkflowName(request, draft))
                .nodes(draft.getNodes())
                .edges(draft.getEdges())
                .positions(draft.getPositions())
                .build();
    }

    private String resolveWorkflowName(AiWorkflowBuildRequestDTO request, WorkflowDefinitionDTO draft) {
        if (request.getWorkflowName() != null && !request.getWorkflowName().isBlank()) {
            return request.getWorkflowName().trim();
        }
        if (draft.getWorkflowName() != null && !draft.getWorkflowName().isBlank()) {
            return draft.getWorkflowName().trim();
        }
        return DEFAULT_WORKFLOW_NAME;
    }

    private AiDatasetSaveResultDTO saveFinalResult(AiWorkflowBuildRequestDTO request,
                                                   AiWorkflowSaveResultDTO saveResult,
                                                   AiWorkflowExecuteResultDTO execution,
                                                   RequestContextDTO context) {
        if (execution == null || execution.getFinalResult() == null) {
            return null;
        }
        if (!ExecutionStatus.SUCCEEDED.name().equals(execution.getStatus())) {
            return null;
        }
        DatasetDTO dataset = toDataset(execution.getFinalResult());
        if (dataset == null) {
            return null;
        }
        SavedDatasetSummaryDTO saved = datasetApplicationService.save(
                SaveDatasetRequestDTO.builder()
                        .name(resolveDatasetName(request, saveResult))
                        .description("AI workflow execution result")
                        .dataset(dataset)
                        .sourceWorkflowId(saveResult.getWorkflowId())
                        .sourceNodeId(execution.getFinalResultNodeId())
                        .build(),
                context.getTenantId(),
                context.getUserId());
        return AiDatasetSaveResultDTO.builder()
                .datasetId(saved.getDatasetId())
                .dataset(saved)
                .build();
    }

    private DatasetDTO toDataset(StandardResultDTO finalResult) {
        if (finalResult == null) {
            return null;
        }
        return finalResult.getDataset();
    }

    private String resolveDatasetName(AiWorkflowBuildRequestDTO request, AiWorkflowSaveResultDTO saveResult) {
        if (request.getWorkflowName() != null && !request.getWorkflowName().isBlank()) {
            return request.getWorkflowName().trim() + "-结果";
        }
        if (saveResult.getWorkflowName() != null && !saveResult.getWorkflowName().isBlank()) {
            return saveResult.getWorkflowName().trim() + "-结果";
        }
        return DEFAULT_DATASET_NAME;
    }

    private Map<String, Object> buildMetadata(AiWorkflowBuildRequestDTO request, String buildMode) {
        return Map.of(
                "legacyCompatible", Boolean.TRUE,
                "runAndSaveRequested", BUILD_MODE_RUN_AND_SAVE.equals(buildMode) || Boolean.TRUE.equals(request.getRunAndSave()),
                "agentEntry", BUILD_MODE_AGENT.equals(buildMode),
                "conversationId", request.getConversationId() == null ? "" : request.getConversationId()
        );
    }
}
