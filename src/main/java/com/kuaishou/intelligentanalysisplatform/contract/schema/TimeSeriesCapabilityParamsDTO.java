package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TimeSeriesCapabilityParamsDTO {
    private List<String> supportedComputeTypes;
    private List<String> supportedGranularities;
    private Boolean compareShiftSupported;
    private Boolean movingAverageSupported;
}
