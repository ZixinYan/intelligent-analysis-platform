package com.kuaishou.intelligentanalysisplatform.contract.schema.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiWorkflowBuildRequestDTO {
    @NotBlank
    private String datasourceId;
    @NotBlank
    private String description;
    /** 工作流名称，不填时由 AI 生成 */
    private String workflowName;
}
