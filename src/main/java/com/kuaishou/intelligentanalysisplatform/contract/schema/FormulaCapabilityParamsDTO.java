package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FormulaCapabilityParamsDTO {
    private Boolean derivedMetric;
    private List<String> supportedOperators;
    private Boolean parenthesisSupported;
}
