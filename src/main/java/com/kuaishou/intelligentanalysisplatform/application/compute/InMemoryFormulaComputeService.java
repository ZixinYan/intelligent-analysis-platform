package com.kuaishou.intelligentanalysisplatform.application.compute;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FormulaFieldDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FormulaNodeConfigDTO;
import org.springframework.stereotype.Service;

@Service
public class InMemoryFormulaComputeService {
    public DatasetDTO compute(FormulaNodeConfigDTO config, DatasetDTO input) {
        List<Map<String, Object>> rows = ComputeSupport.copyRows(input == null ? null : input.getRows());
        for (Map<String, Object> row : rows) {
            for (FormulaFieldDTO formula : config.getFormulas()) {
                row.put(formula.getAlias(), evaluate(row, formula.getExpression()));
            }
        }
        List<FieldSchemaDTO> fields = input == null || input.getSchema() == null || input.getSchema().getFields() == null
                ? new java.util.ArrayList<>()
                : new java.util.ArrayList<>(input.getSchema().getFields());
        for (FormulaFieldDTO formula : config.getFormulas()) {
            fields.add(ComputeSupport.metricField(formula.getAlias()));
        }
        return ComputeSupport.dataset(rows, fields, Map.of("outputRowCount", rows.size()));
    }

    private BigDecimal evaluate(Map<String, Object> row, String expression) {
        List<String> tokens = ComputeSupport.tokenizeExpression(expression);
        Stack<BigDecimal> values = new Stack<>();
        Stack<String> operators = new Stack<>();
        for (String token : tokens) {
            if ("(".equals(token)) {
                operators.push(token);
                continue;
            }
            if (")".equals(token)) {
                while (!operators.isEmpty() && !"(".equals(operators.peek())) {
                    apply(values, operators.pop());
                }
                if (!operators.isEmpty()) {
                    operators.pop();
                }
                continue;
            }
            if (isOperator(token)) {
                while (!operators.isEmpty() && precedence(operators.peek()) >= precedence(token)) {
                    apply(values, operators.pop());
                }
                operators.push(token);
                continue;
            }
            BigDecimal value = ComputeSupport.toDecimal(row.get(token));
            values.push(value == null ? ComputeSupport.toDecimal(token) : value);
        }
        while (!operators.isEmpty()) {
            apply(values, operators.pop());
        }
        return values.isEmpty() ? null : values.pop();
    }

    private void apply(Stack<BigDecimal> values, String operator) {
        BigDecimal right = values.isEmpty() ? null : values.pop();
        BigDecimal left = values.isEmpty() ? null : values.pop();
        values.push(switch (operator) {
            case "+" -> left == null || right == null ? null : left.add(right);
            case "-" -> left == null || right == null ? null : left.subtract(right);
            case "*" -> left == null || right == null ? null : left.multiply(right);
            case "/" -> divide(left, right);
            default -> null;
        });
    }

    private BigDecimal divide(BigDecimal left, BigDecimal right) {
        if (left == null || right == null || BigDecimal.ZERO.compareTo(right) == 0) {
            return null;
        }
        return left.divide(right, 4, java.math.RoundingMode.HALF_UP);
    }

    private boolean isOperator(String token) {
        return "+".equals(token) || "-".equals(token) || "*".equals(token) || "/".equals(token);
    }

    private int precedence(String operator) {
        return switch (operator) {
            case "+", "-" -> 1;
            case "*", "/" -> 2;
            default -> 0;
        };
    }
}
