package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowVersionDTO {
    private String versionId;
    private String workflowId;
    private int versionNumber;
    private String changeSummary;
    private boolean published;
    private String createdBy;
    private long createdAt;
}
