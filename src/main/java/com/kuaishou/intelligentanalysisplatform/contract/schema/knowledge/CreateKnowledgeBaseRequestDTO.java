package com.kuaishou.intelligentanalysisplatform.contract.schema.knowledge;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateKnowledgeBaseRequestDTO {
    @NotBlank
    private String name;
    private String description;
}
