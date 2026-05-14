package com.kuaishou.intelligentanalysisplatform.domain.knowledge;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeDocument {
    private String docId;
    private String knowledgeBaseId;
    private String title;
    private long createdAt;
}
