package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.contract.enums.FilterOperator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilterConditionDTO {
    private String field;
    private FilterOperator operator;
    private Object value;
    private List<Object> values;
}
