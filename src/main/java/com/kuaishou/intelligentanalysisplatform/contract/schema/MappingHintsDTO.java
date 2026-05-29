package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MappingHintsDTO {
    private Map<String, List<String>> chart;
    private Map<String, List<String>> table;
    private Map<String, Object> extensions;
}
