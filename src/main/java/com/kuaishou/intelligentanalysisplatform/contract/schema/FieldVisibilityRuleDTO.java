package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ConditionOperator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldVisibilityRuleDTO {
    private String watchField;
    private ConditionOperator operator;
    private List<Object> targetValues;
    private Boolean visible;
    private Map<String, Object> extensions;
}
