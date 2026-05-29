package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeSeriesCapabilityParamsDTO {
    private List<String> supportedComputeTypes;
    private List<String> supportedGranularities;
    private Boolean compareShiftSupported;
    private Boolean movingAverageSupported;
}
