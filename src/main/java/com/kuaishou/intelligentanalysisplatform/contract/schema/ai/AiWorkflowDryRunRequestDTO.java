package com.kuaishou.intelligentanalysisplatform.contract.schema.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiWorkflowDryRunRequestDTO {
    @NotBlank
    private String workflowId;
}
