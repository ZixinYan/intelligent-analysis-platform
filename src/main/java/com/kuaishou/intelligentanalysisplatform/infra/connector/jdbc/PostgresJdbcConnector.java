package com.kuaishou.intelligentanalysisplatform.infra.connector.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

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

    @Override
    public Map<String, String> listColumnComments(AnalysisDatasource datasource, String tableName) {
        String sql = "SELECT a.attname, d.description"
                + " FROM pg_attribute a"
                + " LEFT JOIN pg_description d ON d.objoid = a.attrelid AND d.objsubid = a.attnum"
                + " JOIN pg_class c ON c.oid = a.attrelid"
                + " WHERE c.relname = ? AND a.attnum > 0 AND NOT a.attisdropped";
        Map<String, String> comments = new LinkedHashMap<>();
        try (Connection conn = getDataSource(datasource).getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String colName = rs.getString(1);
                    String comment = rs.getString(2);
                    if (comment != null && !comment.isBlank()) {
                        comments.put(colName, comment);
                    }
                }
            }
        } catch (Exception e) {
            // 备注获取失败不影响主流程
        }
        return comments;
    }
}
