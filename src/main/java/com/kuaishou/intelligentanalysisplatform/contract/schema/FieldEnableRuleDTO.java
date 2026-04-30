package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ConditionOperator;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FieldEnableRuleDTO {
    private String watchField;
    private ConditionOperator operator;
    private List<Object> targetValues;
    private Boolean enabled;
    private Map<String, Object> extensions;
}
