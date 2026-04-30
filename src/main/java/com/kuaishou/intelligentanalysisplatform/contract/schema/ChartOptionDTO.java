package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChartOptionDTO {
    private Boolean legend;
    private Boolean tooltip;
    private Map<String, Object> extensions;
}
