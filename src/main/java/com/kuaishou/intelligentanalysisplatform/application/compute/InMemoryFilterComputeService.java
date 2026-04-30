package com.kuaishou.intelligentanalysisplatform.application.compute;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.kuaishou.intelligentanalysisplatform.contract.enums.FilterOperator;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FilterConditionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FilterNodeConfigDTO;
import org.springframework.stereotype.Service;

@Service
public class InMemoryFilterComputeService {
    public DatasetDTO compute(FilterNodeConfigDTO config, DatasetDTO input) {
        List<Map<String, Object>> rows = input == null || input.getRows() == null ? List.of() : input.getRows();
        List<Map<String, Object>> filtered = rows.stream().filter(row -> matches(row, config.getConditions())).collect(Collectors.toList());
        return ComputeSupport.dataset(filtered, input == null || input.getSchema() == null ? List.of() : input.getSchema().getFields(), Map.of("outputRowCount", filtered.size()));
    }

    private boolean matches(Map<String, Object> row, List<FilterConditionDTO> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        return conditions.stream().allMatch(condition -> match(row.get(condition.getField()), condition));
    }

    private boolean match(Object actual, FilterConditionDTO condition) {
        FilterOperator operator = condition.getOperator();
        return switch (operator) {
            case EQ -> Objects.equals(actual, condition.getValue());
            case NE -> !Objects.equals(actual, condition.getValue());
            case GT -> compare(actual, condition.getValue()) > 0;
            case GTE -> compare(actual, condition.getValue()) >= 0;
            case LT -> compare(actual, condition.getValue()) < 0;
            case LTE -> compare(actual, condition.getValue()) <= 0;
            case IN -> condition.getValues() != null && condition.getValues().contains(actual);
            case CONTAINS -> actual != null && condition.getValue() != null && String.valueOf(actual).contains(String.valueOf(condition.getValue()));
            case IS_NULL -> actual == null;
            case IS_NOT_NULL -> actual != null;
        };
    }

    private int compare(Object left, Object right) {
        BigDecimal leftDecimal = ComputeSupport.toDecimal(left);
        BigDecimal rightDecimal = ComputeSupport.toDecimal(right);
        if (leftDecimal != null && rightDecimal != null) {
            return leftDecimal.compareTo(rightDecimal);
        }
        return String.valueOf(left).compareTo(String.valueOf(right));
    }
}
