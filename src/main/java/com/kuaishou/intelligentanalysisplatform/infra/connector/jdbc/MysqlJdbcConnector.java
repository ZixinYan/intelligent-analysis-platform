package com.kuaishou.intelligentanalysisplatform.infra.connector.jdbc;

import java.util.regex.Pattern;

import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceType;
import com.kuaishou.intelligentanalysisplatform.domain.datasource.AnalysisDatasource;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.Connector;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.QueryCommand;
import com.kuaishou.intelligentanalysisplatform.infra.connector.pool.HikariPoolRegistry;
import com.kuaishou.intelligentanalysisplatform.infra.query.cancel.QueryCancellationRegistry;
import org.springframework.stereotype.Component;

@Component
public class MysqlJdbcConnector extends AbstractJdbcConnector implements Connector {

    /**
     * 匹配 SQL 末尾已存在的 LIMIT 子句，支持三种写法：
     *   LIMIT n
     *   LIMIT n OFFSET m
     *   LIMIT n, m
     * 在追加分页前先去掉它，避免 "... LIMIT 100 LIMIT 0, 200" 语法错误。
     */
    private static final Pattern TRAILING_LIMIT = Pattern.compile(
            "\\bLIMIT\\s+\\d+(?:\\s+OFFSET\\s+\\d+|\\s*,\\s*\\d+)?\\s*$",
            Pattern.CASE_INSENSITIVE);

    public MysqlJdbcConnector(HikariPoolRegistry poolRegistry, QueryCancellationRegistry cancellationRegistry) {
        super(poolRegistry, cancellationRegistry);
    }

    @Override
    public DatasourceType type() {
        return DatasourceType.MYSQL;
    }

    @Override
    protected String buildListTablesSql(AnalysisDatasource datasource) {
        return "SHOW TABLES";
    }

    /**
     * MySQL 兼容分页：使用 LIMIT offset, count 语法（MySQL 4.0+ 全兼容）。
     * 若用户 SQL 末尾已含 LIMIT 子句，先去除再追加，防止双重 LIMIT 语法错误。
     */
    @Override
    protected String buildPaginatedSql(QueryCommand command) {
        int pageSize = resolvePageSize(command);
        int offset = resolveOffset(command);
        String sql = TRAILING_LIMIT.matcher(command.getNormalizedSql()).replaceAll("").trim();
        return sql + " LIMIT " + offset + ", " + pageSize;
    }
}
