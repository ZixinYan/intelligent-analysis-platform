package com.kuaishou.intelligentanalysisplatform.contract.schema;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ValueSourceType;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ValueType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NodeInputBindingDTO {
    private String name;
    private ValueType valueType;
    private Boolean required;
    private ValueSourceType sourceType;
    private VariableRefDTO variableRef;
    private Object literalValue;
    private String description;
}
