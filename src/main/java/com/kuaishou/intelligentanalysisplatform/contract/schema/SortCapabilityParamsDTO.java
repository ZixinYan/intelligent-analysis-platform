package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SortCapabilityParamsDTO {
    private Boolean multiFieldSupported;
    private Boolean limitSupported;
}
