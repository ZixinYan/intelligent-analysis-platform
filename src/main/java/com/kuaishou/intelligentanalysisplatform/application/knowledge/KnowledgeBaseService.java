package com.kuaishou.intelligentanalysisplatform.application.knowledge;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.contract.schema.KnowledgeChunkDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.knowledge.KnowledgeBaseDTO;
import com.kuaishou.intelligentanalysisplatform.domain.knowledge.KnowledgeBase;

public interface KnowledgeBaseService {
    KnowledgeBaseDTO createKnowledgeBase(String tenantId, String name, String description);
    List<KnowledgeBaseDTO> listKnowledgeBases(String tenantId);
    void deleteKnowledgeBase(String kbId, String tenantId);
    void ingestDocument(String kbId, String docId, String docTitle, String content, String tenantId);
    void deleteDocument(String kbId, String docId, String tenantId);
    List<KnowledgeChunkDTO> retrieve(String kbId, String query, int topK);
}
