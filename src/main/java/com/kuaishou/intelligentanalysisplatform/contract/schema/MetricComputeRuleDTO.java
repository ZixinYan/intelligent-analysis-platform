package com.kuaishou.intelligentanalysisplatform.contract.schema;

import com.kuaishou.intelligentanalysisplatform.contract.enums.CompareUnit;
import com.kuaishou.intelligentanalysisplatform.contract.enums.TimeSeriesComputeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricComputeRuleDTO {
    private String metricField;
    private TimeSeriesComputeType computeType;
    private String alias;
    private Integer compareShift;
    private CompareUnit compareUnit;
    private Integer windowSize;
    private String format;
}
