package com.kuaishou.intelligentanalysisplatform.domain.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowRunLog {
    private String runId;
    private String workflowId;
    private String versionId;
    private String tenantId;
    private String triggerType;
    private String status;
    private Integer nodeCount;
    private long startedAt;
    private Long finishedAt;
    private Long elapsedMs;
    private String nodeTraceJson;
    private String createdBy;
}
