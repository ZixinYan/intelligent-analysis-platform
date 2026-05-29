package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunkDTO {
    private String id;
    private String docId;
    private String docTitle;
    private String content;
    private double score;
    private int chunkIndex;
}
