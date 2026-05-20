package com.kuaishou.intelligentanalysisplatform.infra.connector.jdbc;

import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceType;
import com.kuaishou.intelligentanalysisplatform.domain.datasource.AnalysisDatasource;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.Connector;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.QueryCommand;
import com.kuaishou.intelligentanalysisplatform.infra.connector.pool.HikariPoolRegistry;
import com.kuaishou.intelligentanalysisplatform.infra.query.cancel.QueryCancellationRegistry;
import org.springframework.stereotype.Component;

@Component
public class MysqlJdbcConnector extends AbstractJdbcConnector implements Connector {
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
     * MySQL 兼容语法：LIMIT offset, count（支持所有 MySQL 4.0+ 版本）
     * 父类使用的 LIMIT count OFFSET offset 语法仅在 MySQL 4.0.6+ 才支持
     */
    @Override
    protected String buildPaginatedSql(QueryCommand command) {
        int pageSize = resolvePageSize(command);
        int offset = resolveOffset(command);
        return command.getNormalizedSql() + " LIMIT " + offset + ", " + pageSize;
    }
}
