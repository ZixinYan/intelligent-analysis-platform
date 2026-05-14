package com.kuaishou.intelligentanalysisplatform.contract.schema.knowledge;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeBaseDTO {
    private String id;
    private String tenantId;
    private String name;
    private String description;
    private long createdAt;
    private long updatedAt;
}
