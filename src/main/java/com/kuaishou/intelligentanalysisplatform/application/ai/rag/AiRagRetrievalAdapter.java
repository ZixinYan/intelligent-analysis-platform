package com.kuaishou.intelligentanalysisplatform.application.ai.rag;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.application.knowledge.KnowledgeBaseService;
import com.kuaishou.intelligentanalysisplatform.contract.schema.KnowledgeChunkDTO;
import org.springframework.stereotype.Component;

@Component
public class AiRagRetrievalAdapter {

    private final KnowledgeBaseService knowledgeBaseService;

    public AiRagRetrievalAdapter(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    public List<KnowledgeChunkDTO> retrieve(String knowledgeBaseId, String queryText, int topK) {
        return knowledgeBaseService.retrieve(knowledgeBaseId, queryText, topK);
    }
}
