package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class AggregateNodeConfigDTO extends BaseNodeConfigDTO {
    private VariableRefDTO datasetRef;
    private List<String> groupByFields;
    private List<AggregateMetricDTO> metrics;
    private List<SortFieldDTO> sortFields;
    private Integer topN;
    private Boolean pushdownEnabled;
}
