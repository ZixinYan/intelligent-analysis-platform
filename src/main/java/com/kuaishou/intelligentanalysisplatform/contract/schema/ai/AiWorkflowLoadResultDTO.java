package com.kuaishou.intelligentanalysisplatform.contract.schema.ai;

import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowDefinitionDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiWorkflowLoadResultDTO {
    private String workflowId;
    private WorkflowDefinitionDTO workflow;
}
