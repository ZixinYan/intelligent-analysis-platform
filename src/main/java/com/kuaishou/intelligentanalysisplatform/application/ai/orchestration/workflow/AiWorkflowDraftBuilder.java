package com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.workflow;

import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowDefinitionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowBuildRequestDTO;

public interface AiWorkflowDraftBuilder {

    WorkflowDefinitionDTO buildDraft(AiWorkflowBuildRequestDTO request, RequestContextDTO context);
}
