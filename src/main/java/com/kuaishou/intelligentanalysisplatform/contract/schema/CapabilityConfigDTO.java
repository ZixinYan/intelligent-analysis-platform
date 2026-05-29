package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CapabilityConfigDTO {
    private List<String> supportedComputeTypes;
    private List<String> supportedGranularities;
    private Boolean sqlPushdownSupported;
    private String pushdownBoundary;
    private List<String> inputConstraints;
    private List<String> outputGuarantees;
    private Map<String, Object> extensions;
}
