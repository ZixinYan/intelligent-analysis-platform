package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FilterCapabilityParamsDTO {
    private List<String> supportedOperators;
    private Boolean multipleConditionsSupported;
}
