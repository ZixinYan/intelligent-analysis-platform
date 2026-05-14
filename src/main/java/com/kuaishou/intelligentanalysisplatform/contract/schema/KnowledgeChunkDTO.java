package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeChunkDTO {
    private String id;
    private String docId;
    private String docTitle;
    private String content;
    private double score;
    private int chunkIndex;
}
