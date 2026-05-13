package com.kuaishou.intelligentanalysisplatform.contract.schema.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiSqlRequestDTO {
    @NotBlank
    private String datasourceId;
    @NotBlank
    private String tableName;
    @NotBlank
    private String description;
}
