package com.kuaishou.intelligentanalysisplatform.infra.connector.jdbc;

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
    protected void configureTimeout(java.sql.PreparedStatement statement, QueryCommand command) {
    }
}
