package com.kuaishou.intelligentanalysisplatform.contract.schema;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ChartType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ChartOutputNodeConfigDTO extends BaseNodeConfigDTO {
    private VariableRefDTO datasetRef;
    private ChartType chartType;
    private ChartMappingDTO mapping;
    private ChartOptionDTO option;
}
