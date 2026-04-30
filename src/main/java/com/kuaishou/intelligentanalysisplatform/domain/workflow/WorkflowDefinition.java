package com.kuaishou.intelligentanalysisplatform.domain.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDefinition {
    private String workflowId;
    private String tenantId;
    private String workflowName;
    private String definitionJson;
    private String operatorId;
    private Long createdAt;
    private Long updatedAt;
}
