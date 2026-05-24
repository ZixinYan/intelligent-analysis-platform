package com.kuaishou.intelligentanalysisplatform.application.ai.rag;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AiRagQuery {

    private final String knowledgeBaseId;
    private final String queryText;
    private final Integer topK;
    private final AiRagScene scene;
}
