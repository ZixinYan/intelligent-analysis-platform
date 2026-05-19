package com.kuaishou.intelligentanalysisplatform.infra.connector.jdbc;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceType;
import com.kuaishou.intelligentanalysisplatform.domain.datasource.AnalysisDatasource;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.Connector;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.QueryCommand;
import com.kuaishou.intelligentanalysisplatform.infra.connector.pool.HikariPoolRegistry;
import com.kuaishou.intelligentanalysisplatform.infra.query.cancel.QueryCancellationRegistry;
import org.springframework.stereotype.Component;

@Component
public class ClickHouseJdbcConnector extends AbstractJdbcConnector implements Connector {
    public ClickHouseJdbcConnector(HikariPoolRegistry poolRegistry, QueryCancellationRegistry cancellationRegistry) {
        super(poolRegistry, cancellationRegistry);
    }

    @Override
    public DatasourceType type() {
        return DatasourceType.CLICKHOUSE;
    }

    @Override
    protected String buildListTablesSql(AnalysisDatasource datasource) {
        return "SELECT name FROM system.tables WHERE database = currentDatabase() ORDER BY name";
    }

    @Override
    protected void configureTimeout(PreparedStatement statement, QueryCommand command) throws SQLException {
        Integer timeoutMs = command.getTimeoutMs();
        if (timeoutMs == null || timeoutMs <= 0) {
            return;
        }
        statement.setQueryTimeout(Math.max(1, timeoutMs / 1000));
        statement.setFetchSize(256);
    }
}
