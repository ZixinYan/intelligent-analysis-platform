package com.kuaishou.intelligentanalysisplatform.application.ai.rag;

public interface AiRagOrchestrationService {

    AiRagResult retrieve(AiRagQuery query);
}
