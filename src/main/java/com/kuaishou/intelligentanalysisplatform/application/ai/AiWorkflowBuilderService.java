package com.kuaishou.intelligentanalysisplatform.application.ai;

import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowDefinitionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowBuildRequestDTO;

/**
 * AI 工作流自动构建服务。
 * 根据自然语言描述生成完整的 WorkflowDefinitionDTO 草稿。
 */
public interface AiWorkflowBuilderService {
    WorkflowDefinitionDTO buildDraft(AiWorkflowBuildRequestDTO request, RequestContextDTO context);
}
