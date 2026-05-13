package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowRunLogDTO {
    private String runId;
    private String workflowId;
    private String versionId;
    private String tenantId;
    private String status;
    private String triggerType;
    private Integer nodeCount;
    private Long startedAt;
    private Long finishedAt;
    private Long elapsedMs;
    private String createdBy;
    private List<NodeTraceDTO> nodeTraces;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeTraceDTO {
        private String nodeId;
        private String nodeType;
        private String status;
        private Long elapsedMs;
        private Boolean cached;
        private Integer rowCount;
        private Boolean pushdown;
        private String error;
    }
}
