package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormulaCapabilityParamsDTO {
    private Boolean derivedMetric;
    private List<String> supportedOperators;
    private Boolean parenthesisSupported;
}
