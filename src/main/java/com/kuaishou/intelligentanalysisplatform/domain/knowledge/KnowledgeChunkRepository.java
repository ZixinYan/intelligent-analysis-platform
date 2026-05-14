package com.kuaishou.intelligentanalysisplatform.domain.knowledge;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.contract.schema.KnowledgeChunkDTO;

public interface KnowledgeChunkRepository {
    void saveAll(List<KnowledgeChunk> chunks);
    void deleteByKbIdAndDocId(String knowledgeBaseId, String docId);
    void deleteByKnowledgeBaseId(String knowledgeBaseId);
    List<KnowledgeChunkDTO> findTopKByCosine(String knowledgeBaseId, float[] queryEmbedding, int topK);
}
