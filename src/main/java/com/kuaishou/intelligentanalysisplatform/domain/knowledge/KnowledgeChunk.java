package com.kuaishou.intelligentanalysisplatform.domain.knowledge;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeChunk {
    private String id;
    private String knowledgeBaseId;
    private String docId;
    private String docTitle;
    private String content;
    private float[] embedding;
    private int chunkIndex;
    private long createdAt;
}
