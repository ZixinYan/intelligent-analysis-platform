package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PivotCapabilityParamsDTO {
    private Boolean rowFieldRequired;
    private Boolean columnFieldRequired;
    private Boolean valueFieldRequired;
    private Boolean aggregateFieldSupported;
}
