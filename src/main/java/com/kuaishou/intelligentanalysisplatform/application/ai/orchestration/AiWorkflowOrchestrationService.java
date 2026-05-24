package com.kuaishou.intelligentanalysisplatform.application.ai.orchestration;

import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowDefinitionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowBuildRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowBuildResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowDryRunRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowDryRunResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowExecuteRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowExecuteResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowLoadResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowSaveRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowSaveResultDTO;

public interface AiWorkflowOrchestrationService {

    WorkflowDefinitionDTO buildDraft(AiWorkflowBuildRequestDTO request, RequestContextDTO context);

    AiWorkflowBuildResultDTO build(AiWorkflowBuildRequestDTO request, RequestContextDTO context);

    AiWorkflowSaveResultDTO saveDraft(AiWorkflowSaveRequestDTO request, RequestContextDTO context);

    AiWorkflowLoadResultDTO loadWorkflow(String workflowId, RequestContextDTO context);

    AiWorkflowExecuteResultDTO executeWorkflow(AiWorkflowExecuteRequestDTO request, RequestContextDTO context);

    AiWorkflowDryRunResultDTO dryRunWorkflow(AiWorkflowDryRunRequestDTO request, RequestContextDTO context);
}
