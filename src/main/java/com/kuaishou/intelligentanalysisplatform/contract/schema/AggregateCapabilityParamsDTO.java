package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AggregateCapabilityParamsDTO {
    private List<String> supportedFunctions;
    private Boolean topNSupported;
    private Boolean inlineSortSupported;
}
