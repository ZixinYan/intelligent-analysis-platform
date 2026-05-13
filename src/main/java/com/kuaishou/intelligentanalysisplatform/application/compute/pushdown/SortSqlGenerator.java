package com.kuaishou.intelligentanalysisplatform.application.compute.pushdown;

import com.kuaishou.intelligentanalysisplatform.contract.schema.SortNodeConfigDTO;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Sort 算子下推 SQL 生成器。
 *
 * <p>对应 InMemorySortComputeService 的排序语义，生成如下结构：
 * <pre>
 *   SELECT * FROM ({baseQuery}) AS _base ORDER BY {sortFields} [LIMIT limit]
 * </pre>
 */
@Component
public class SortSqlGenerator implements CapabilitySqlGenerator<SortNodeConfigDTO> {

    @Override
    public String capabilityCode() {
        return "sort";
    }

    @Override
    public String generate(String baseQuery, SortNodeConfigDTO config, DatasourceDialect dialect) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM (").append(baseQuery).append(") AS _base");

        if (config.getSortFields() != null && !config.getSortFields().isEmpty()) {
            String orderBy = config.getSortFields().stream()
                    .map(f -> quoteIdentifier(f.getField(), dialect)
                            + " " + ("DESC".equalsIgnoreCase(f.getOrder()) ? "DESC" : "ASC"))
                    .collect(Collectors.joining(", "));
            sql.append(" ORDER BY ").append(orderBy);
        }

        if (config.getLimit() != null && config.getLimit() > 0) {
            sql.append(" LIMIT ").append(config.getLimit());
        }

        return sql.toString();
    }

    private String quoteIdentifier(String identifier, DatasourceDialect dialect) {
        return switch (dialect) {
            case MYSQL -> "`" + identifier.replace("`", "``") + "`";
            case POSTGRES, CLICKHOUSE -> "\"" + identifier.replace("\"", "\"\"") + "\"";
        };
    }
}
