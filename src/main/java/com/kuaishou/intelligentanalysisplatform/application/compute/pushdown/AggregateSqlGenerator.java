package com.kuaishou.intelligentanalysisplatform.application.compute.pushdown;

import com.kuaishou.intelligentanalysisplatform.contract.enums.AggregateFunction;
import com.kuaishou.intelligentanalysisplatform.contract.schema.AggregateMetricDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.AggregateNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SortFieldDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Aggregate 算子下推 SQL 生成器。
 *
 * <p>对应 InMemoryAggregateComputeService 的计算语义，生成如下结构：
 * <pre>
 *   SELECT {groupByFields}, {metrics(SUM/COUNT/AVG/MAX/MIN)}
 *   FROM ({baseQuery}) AS _base
 *   GROUP BY {groupByFields}
 *   [ORDER BY {sortFields}]
 *   [LIMIT {topN}]
 * </pre>
 *
 * <p>函数映射：
 * <ul>
 *   <li>COUNT → COUNT(*)</li>
 *   <li>COUNT_DISTINCT → COUNT(DISTINCT field)</li>
 *   <li>SUM → SUM(field)</li>
 *   <li>AVG → AVG(field)</li>
 *   <li>MAX → MAX(field)</li>
 *   <li>MIN → MIN(field)</li>
 * </ul>
 */
@Component
public class AggregateSqlGenerator implements CapabilitySqlGenerator<AggregateNodeConfigDTO> {

    @Override
    public String capabilityCode() {
        return "aggregate";
    }

    @Override
    public String generate(String baseQuery, AggregateNodeConfigDTO config, DatasourceDialect dialect) {
        List<String> selectParts = new ArrayList<>();
        List<String> groupByParts = new ArrayList<>();

        // 维度字段
        List<String> groupByFields = config.getGroupByFields() == null ? List.of() : config.getGroupByFields();
        for (String field : groupByFields) {
            String quoted = quoteIdentifier(field, dialect);
            selectParts.add(quoted);
            groupByParts.add(quoted);
        }

        // 指标字段
        for (AggregateMetricDTO metric : config.getMetrics()) {
            String aggExpr = buildAggExpr(metric, dialect);
            String alias = resolveMetricName(metric);
            selectParts.add(aggExpr + " AS " + quoteIdentifier(alias, dialect));
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(String.join(", ", selectParts));
        sql.append(" FROM (").append(baseQuery).append(") AS _base");

        if (!groupByParts.isEmpty()) {
            sql.append(" GROUP BY ").append(String.join(", ", groupByParts));
        }

        if (config.getSortFields() != null && !config.getSortFields().isEmpty()) {
            String orderBy = config.getSortFields().stream()
                    .map(f -> buildOrderByClause(f, dialect))
                    .collect(Collectors.joining(", "));
            sql.append(" ORDER BY ").append(orderBy);
        }

        if (config.getTopN() != null && config.getTopN() > 0) {
            sql.append(" LIMIT ").append(config.getTopN());
        }

        return sql.toString();
    }

    private String buildAggExpr(AggregateMetricDTO metric, DatasourceDialect dialect) {
        AggregateFunction func = metric.getAgg();
        String field = quoteIdentifier(metric.getField(), dialect);
        return switch (func) {
            case COUNT -> "COUNT(*)";
            case COUNT_DISTINCT -> "COUNT(DISTINCT " + field + ")";
            case SUM -> "SUM(" + field + ")";
            case AVG -> "AVG(" + field + ")";
            case MAX -> "MAX(" + field + ")";
            case MIN -> "MIN(" + field + ")";
        };
    }

    private String buildOrderByClause(SortFieldDTO sortField, DatasourceDialect dialect) {
        String direction = "DESC".equalsIgnoreCase(sortField.getOrder()) ? "DESC" : "ASC";
        return quoteIdentifier(sortField.getField(), dialect) + " " + direction;
    }

    private String resolveMetricName(AggregateMetricDTO metric) {
        return metric.getAlias() != null && !metric.getAlias().isBlank()
                ? metric.getAlias()
                : metric.getField() + "_" + metric.getAgg().name().toLowerCase();
    }

    private String quoteIdentifier(String identifier, DatasourceDialect dialect) {
        return switch (dialect) {
            case MYSQL -> "`" + identifier.replace("`", "``") + "`";
            case POSTGRES, CLICKHOUSE -> "\"" + identifier.replace("\"", "\"\"") + "\"";
        };
    }
}
