package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.contract.enums.FilterOperator;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FilterConditionDTO {
    private String field;
    private FilterOperator operator;
    private Object value;
    private List<Object> values;
}
