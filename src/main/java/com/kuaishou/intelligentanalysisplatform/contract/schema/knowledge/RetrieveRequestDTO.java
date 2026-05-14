package com.kuaishou.intelligentanalysisplatform.contract.schema.knowledge;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RetrieveRequestDTO {
    @NotBlank
    private String query;
    @Min(1)
    private int topK = 5;
}
