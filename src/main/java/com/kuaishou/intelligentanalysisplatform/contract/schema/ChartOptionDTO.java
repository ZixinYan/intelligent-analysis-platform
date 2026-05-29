package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChartOptionDTO {
    private Boolean legend;
    private Boolean tooltip;
    private Map<String, Object> extensions;
}
