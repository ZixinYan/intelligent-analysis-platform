package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MappingHintsDTO {
    private Map<String, List<String>> chart;
    private Map<String, List<String>> table;
    private Map<String, Object> extensions;
}
