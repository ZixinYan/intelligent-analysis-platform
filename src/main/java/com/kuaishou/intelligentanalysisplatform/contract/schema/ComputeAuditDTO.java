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
public class ComputeAuditDTO {
    private String capabilityType;
    private String executionBoundary;
    private List<ComputeStepDTO> steps;
    private Integer inputRowCount;
    private Integer outputRowCount;
    private String derivedMetricNote;
}
