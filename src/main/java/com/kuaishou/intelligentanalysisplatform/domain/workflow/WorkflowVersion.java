package com.kuaishou.intelligentanalysisplatform.domain.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowVersion {
    private String versionId;
    private String workflowId;
    private String tenantId;
    private int versionNumber;
    private String definitionJson;
    private String changeSummary;
    private boolean published;
    private String createdBy;
    private long createdAt;
}
