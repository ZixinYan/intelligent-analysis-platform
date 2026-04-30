package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.contract.enums.TimeGranularity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class TimeSeriesComputeNodeConfigDTO extends BaseNodeConfigDTO {
    private VariableRefDTO datasetRef;
    private String timeField;
    private List<String> dimensionFields;
    private List<MetricComputeRuleDTO> metrics;
    private TimeGranularity granularity;
    private Boolean pushdownEnabled;
}
