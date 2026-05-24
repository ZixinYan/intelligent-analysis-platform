package com.kuaishou.intelligentanalysisplatform.contract.schema.ai;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiWorkflowBuildRequestDTO {
    @NotBlank
    private String datasourceId;
    @NotBlank
    private String description;
    private String workflowName;
    private String buildMode;
    private String responseMode;
    private String agentTaskId;
    private Boolean runAndSave;
    private String conversationId;
    private List<AiClarificationAnswerDTO> clarificationAnswers;
}
