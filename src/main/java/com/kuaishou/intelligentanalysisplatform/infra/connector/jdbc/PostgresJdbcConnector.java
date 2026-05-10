package com.kuaishou.intelligentanalysisplatform.infra.connector.jdbc;

import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceType;
import com.kuaishou.intelligentanalysisplatform.domain.datasource.AnalysisDatasource;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.Connector;
import com.kuaishou.intelligentanalysisplatform.infra.connector.pool.HikariPoolRegistry;
import com.kuaishou.intelligentanalysisplatform.infra.query.cancel.QueryCancellationRegistry;
import org.springframework.stereotype.Component;

@Component
public class PostgresJdbcConnector extends AbstractJdbcConnector implements Connector {
    public PostgresJdbcConnector(HikariPoolRegistry poolRegistry, QueryCancellationRegistry cancellationRegistry) {
        super(poolRegistry, cancellationRegistry);
    }

    @Override
    public DatasourceType type() {
        return DatasourceType.POSTGRES;
    }

    @Override
    protected String buildListTablesSql(AnalysisDatasource datasource) {
        return "SELECT table_name FROM information_schema.tables"
                + " WHERE table_schema = current_schema()"
                + " AND table_type IN ('BASE TABLE', 'VIEW')"
                + " ORDER BY table_name";
    }
}
