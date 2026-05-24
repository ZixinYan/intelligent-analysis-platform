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
public class AiWorkflowSaveResultDTO {
    private String workflowId;
    private String workflowName;
    private WorkflowDefinitionDTO workflow;
    private boolean created;
}
