package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QueryParameterDTO {
    private String name;
    private VariableRefDTO variableRef;
    private Object defaultValue;
}
