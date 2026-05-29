package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeRunMetaDTO {
    private Long elapsedMs;
    private Boolean cached;
    private String taskId;
    private String capabilityType;
    private String computeEngine;
    private Boolean pushdownApplied;
    private String executionBoundary;
    private ComputeAuditDTO audit;
}
