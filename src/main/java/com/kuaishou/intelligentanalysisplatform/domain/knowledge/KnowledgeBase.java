package com.kuaishou.intelligentanalysisplatform.domain.knowledge;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeBase {
    private String id;
    private String tenantId;
    private String name;
    private String description;
    private long createdAt;
    private long updatedAt;
}
