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
public class AggregateCapabilityParamsDTO {
    private List<String> supportedFunctions;
    private Boolean topNSupported;
    private Boolean inlineSortSupported;
}
