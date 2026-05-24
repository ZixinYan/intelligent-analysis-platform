package com.kuaishou.intelligentanalysisplatform.contract.schema.ai;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiWorkflowExecuteRequestDTO {
    @NotBlank
    private String workflowId;
    private Map<String, Object> inputs;
}
