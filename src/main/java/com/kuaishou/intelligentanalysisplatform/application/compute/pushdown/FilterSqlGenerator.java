package com.kuaishou.intelligentanalysisplatform.application.compute.pushdown;

import com.kuaishou.intelligentanalysisplatform.contract.schema.FilterConditionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FilterNodeConfigDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Filter 算子下推 SQL 生成器。
 *
 * <p>对应 InMemoryFilterComputeService 的过滤语义，生成如下结构：
 * <pre>
 *   SELECT * FROM ({baseQuery}) AS _base WHERE {conditions}
 * </pre>
 *
 * <p>条件之间以 AND 连接（与 in-memory 实现一致）。
 */
@Component
public class FilterSqlGenerator implements CapabilitySqlGenerator<FilterNodeConfigDTO> {

    @Override
    public String capabilityCode() {
        return "filter";
    }

    @Override
    public String generate(String baseQuery, FilterNodeConfigDTO config, DatasourceDialect dialect) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM (").append(baseQuery).append(") AS _base");

        List<FilterConditionDTO> conditions = config.getConditions();
        if (conditions != null && !conditions.isEmpty()) {
            String whereClause = conditions.stream()
                    .map(c -> buildCondition(c, dialect))
                    .collect(Collectors.joining(" AND "));
            sql.append(" WHERE ").append(whereClause);
        }

        return sql.toString();
    }

    private String buildCondition(FilterConditionDTO condition, DatasourceDialect dialect) {
        String field = quoteIdentifier(condition.getField(), dialect);
        return switch (condition.getOperator()) {
            case EQ -> field + " = " + formatValue(condition.getValue());
            case NE -> field + " != " + formatValue(condition.getValue());
            case GT -> field + " > " + formatValue(condition.getValue());
            case GTE -> field + " >= " + formatValue(condition.getValue());
            case LT -> field + " < " + formatValue(condition.getValue());
            case LTE -> field + " <= " + formatValue(condition.getValue());
            case IN -> field + " IN (" + formatValueList(condition.getValues()) + ")";
            case CONTAINS -> field + " LIKE " + formatLikeValue(condition.getValue());
            case IS_NULL -> field + " IS NULL";
            case IS_NOT_NULL -> field + " IS NOT NULL";
        };
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof Boolean) {
            return ((Boolean) value) ? "1" : "0";
        }
        // String: escape single quotes
        return "'" + String.valueOf(value).replace("'", "''") + "'";
    }

    private String formatLikeValue(Object value) {
        if (value == null) {
            return "'%%'";
        }
        String escaped = String.valueOf(value)
                .replace("'", "''")
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "'%" + escaped + "%'";
    }

    private String formatValueList(List<Object> values) {
        if (values == null || values.isEmpty()) {
            return "NULL";
        }
        return values.stream().map(this::formatValue).collect(Collectors.joining(", "));
    }

    private String quoteIdentifier(String identifier, DatasourceDialect dialect) {
        return switch (dialect) {
            case MYSQL -> "`" + identifier.replace("`", "``") + "`";
            case POSTGRES, CLICKHOUSE -> "\"" + identifier.replace("\"", "\"\"") + "\"";
        };
    }
}
