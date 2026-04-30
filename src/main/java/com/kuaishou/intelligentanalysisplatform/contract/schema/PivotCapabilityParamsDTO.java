package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PivotCapabilityParamsDTO {
    private Boolean rowFieldRequired;
    private Boolean columnFieldRequired;
    private Boolean valueFieldRequired;
    private Boolean aggregateFieldSupported;
}
