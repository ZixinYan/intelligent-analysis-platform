package com.kuaishou.intelligentanalysisplatform.infra.connector.jdbc;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import com.kuaishou.intelligentanalysisplatform.contract.enums.FieldSemanticType;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ValueType;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.domain.datasource.AnalysisDatasource;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.Connector;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.HealthCheckResult;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.PaginationMode;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.QueryCommand;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.QueryResult;
import com.kuaishou.intelligentanalysisplatform.infra.connector.pool.HikariPoolRegistry;
import com.kuaishou.intelligentanalysisplatform.infra.query.cancel.QueryCancellationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractJdbcConnector implements Connector {
    private static final Logger log = LoggerFactory.getLogger(AbstractJdbcConnector.class);

    private final HikariPoolRegistry poolRegistry;
    private final QueryCancellationRegistry cancellationRegistry;

    protected AbstractJdbcConnector(HikariPoolRegistry poolRegistry, QueryCancellationRegistry cancellationRegistry) {
        this.poolRegistry = poolRegistry;
        this.cancellationRegistry = cancellationRegistry;
    }

    @Override
    public QueryResult execute(AnalysisDatasource datasource, QueryCommand command) {
        long start = System.currentTimeMillis();
        DataSource dataSource = poolRegistry.getOrCreate(datasource);
        String sql = buildPaginatedSql(command);
        int effectiveOffset = resolveOffset(command);
        int effectivePageSize = resolvePageSize(command);
        int maxRows = resolveMaxRows(command, effectivePageSize);
        log.info("Executing query: datasourceId={}, type={}, queryId={}, timeoutMs={}, pageSize={}, maxRows={}, offset={}",
                datasource.getId(), datasource.getType(), command.getQueryId(), command.getTimeoutMs(), effectivePageSize, maxRows, effectiveOffset);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            configureStatement(statement, command);
            cancellationRegistry.register(command.getQueryId(), statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                ResultSetMetaData metaData = resultSet.getMetaData();
                List<FieldSchemaDTO> fields = mapFields(metaData);
                List<Map<String, Object>> rows = new ArrayList<>();
                boolean truncated = false;
                while (resultSet.next()) {
                    if (rows.size() >= maxRows) {
                        truncated = true;
                        break;
                    }
                    rows.add(readRow(resultSet, metaData));
                }
                QueryResult result = QueryResult.builder()
                        .fields(fields)
                        .rows(rows)
                        .rowCount(rows.size())
                        .truncated(truncated)
                        .nextCursor(buildNextCursor(command, effectiveOffset, effectivePageSize, rows.size(), truncated))
                        .elapsedMs(System.currentTimeMillis() - start)
                        .cached(false)
                        .build();
                log.info("Query execution succeeded: datasourceId={}, queryId={}, rowCount={}, truncated={}, elapsedMs={}",
                        datasource.getId(), command.getQueryId(), result.getRowCount(), result.getTruncated(), result.getElapsedMs());
                return result;
            } finally {
                cancellationRegistry.deregister(command.getQueryId());
            }
        } catch (SQLException e) {
            log.error("Query execution failed: datasourceId={}, queryId={}, sqlState={}, errorCode={}, message={}",
                    datasource.getId(), command.getQueryId(), e.getSQLState(), e.getErrorCode(), e.getMessage(), e);
            throw new IllegalStateException("query execution failed", e);
        }
    }

    @Override
    public List<String> listTables(AnalysisDatasource datasource) {
        DataSource dataSource = poolRegistry.getOrCreate(datasource);
        try (Connection connection = dataSource.getConnection();
             java.sql.Statement stmt = connection.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery(buildListTablesSql(datasource))) {
            List<String> tables = new ArrayList<>();
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
            return tables;
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("list tables failed: " + e.getMessage(), e);
        }
    }

    protected abstract String buildListTablesSql(AnalysisDatasource datasource);

    @Override
    public HealthCheckResult healthCheck(AnalysisDatasource datasource) {
        long start = System.currentTimeMillis();
        DataSource dataSource = poolRegistry.getOrCreate(datasource);
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(3)) {
                log.warn("Datasource health check returned invalid connection: datasourceId={}, type={}",
                        datasource.getId(), datasource.getType());
                return new HealthCheckResult(false, System.currentTimeMillis() - start, null, "connection invalid");
            }
            String serverVersion = connection.getMetaData().getDatabaseProductVersion();
            long elapsedMs = System.currentTimeMillis() - start;
            log.info("Datasource health check succeeded: datasourceId={}, type={}, elapsedMs={}, serverVersion={}",
                    datasource.getId(), datasource.getType(), elapsedMs, serverVersion);
            return new HealthCheckResult(true, elapsedMs, serverVersion, "connection ok");
        } catch (SQLException e) {
            long elapsedMs = System.currentTimeMillis() - start;
            log.error("Datasource health check failed: datasourceId={}, type={}, elapsedMs={}, sqlState={}, errorCode={}, message={}",
                    datasource.getId(), datasource.getType(), elapsedMs, e.getSQLState(), e.getErrorCode(), e.getMessage(), e);
            return new HealthCheckResult(false, elapsedMs, null, e.getMessage());
        }
    }

    @Override
    public List<FieldSchemaDTO> inferSchema(AnalysisDatasource datasource, QueryCommand command) {
        DataSource dataSource = poolRegistry.getOrCreate(datasource);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(buildSchemaSql(command.getNormalizedSql()))) {
            configureTimeout(statement, command);
            try (ResultSet resultSet = statement.executeQuery()) {
                return mapFields(resultSet.getMetaData());
            }
        } catch (SQLException e) {
            throw new IllegalStateException("schema infer failed", e);
        }
    }

    protected String buildPaginatedSql(QueryCommand command) {
        int pageSize = resolvePageSize(command);
        int offset = resolveOffset(command);
        return command.getNormalizedSql() + " LIMIT " + pageSize + " OFFSET " + offset;
    }

    protected String buildSchemaSql(String normalizedSql) {
        return "SELECT * FROM (" + normalizedSql + ") t LIMIT 0";
    }

    protected void configureStatement(PreparedStatement statement, QueryCommand command) throws SQLException {
        configureTimeout(statement, command);
        statement.setMaxRows(resolveMaxRows(command, resolvePageSize(command)) + 1);
    }

    protected void configureTimeout(PreparedStatement statement, QueryCommand command) throws SQLException {
        Integer timeoutMs = command.getTimeoutMs();
        if (timeoutMs != null && timeoutMs > 0) {
            statement.setQueryTimeout(Math.max(1, timeoutMs / 1000));
        }
    }

    protected int resolvePageSize(QueryCommand command) {
        Integer pageSize = command.getPageSize();
        return pageSize == null || pageSize <= 0 ? 200 : pageSize;
    }

    protected int resolveOffset(QueryCommand command) {
        if (command.getPaginationMode() == PaginationMode.CURSOR && command.getCursor() != null && !command.getCursor().isBlank()) {
            return decodeCursor(command.getCursor());
        }
        Integer offset = command.getOffset();
        return offset == null || offset < 0 ? 0 : offset;
    }

    protected int resolveMaxRows(QueryCommand command, int pageSize) {
        Integer maxRows = command.getMaxRows();
        if (maxRows == null || maxRows <= 0) {
            return pageSize;
        }
        return Math.min(maxRows, pageSize);
    }

    protected String buildNextCursor(QueryCommand command, int offset, int pageSize, int rowCount, boolean truncated) {
        if (command.getPaginationMode() != PaginationMode.CURSOR) {
            return null;
        }
        if (!truncated && rowCount < pageSize) {
            return null;
        }
        return Base64.getEncoder().encodeToString(String.valueOf(offset + rowCount).getBytes(StandardCharsets.UTF_8));
    }

    protected int decodeCursor(String cursor) {
        try {
            return Integer.parseInt(new String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return 0;
        }
    }

    protected List<FieldSchemaDTO> mapFields(ResultSetMetaData metaData) throws SQLException {
        List<FieldSchemaDTO> fields = new ArrayList<>();
        for (int index = 1; index <= metaData.getColumnCount(); index++) {
            String name = metaData.getColumnLabel(index);
            ValueType valueType = toValueType(metaData.getColumnType(index));
            fields.add(FieldSchemaDTO.builder()
                    .fieldId(UUID.randomUUID().toString())
                    .name(name)
                    .path(List.of(name))
                    .valueType(valueType)
                    .nullable(metaData.isNullable(index) != ResultSetMetaData.columnNoNulls)
                    .displayName(name)
                    .semanticType(toSemanticType(name, valueType))
                    .capabilities(List.of())
                    .sampleValues(List.of())
                    .stats(Map.of())
                    .extensions(Map.of())
                    .build());
        }
        return fields;
    }

    protected Map<String, Object> readRow(ResultSet resultSet, ResultSetMetaData metaData) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 1; index <= metaData.getColumnCount(); index++) {
            row.put(metaData.getColumnLabel(index), resultSet.getObject(index));
        }
        return row;
    }

    protected ValueType toValueType(int jdbcType) {
        return switch (jdbcType) {
            case Types.INTEGER, Types.SMALLINT, Types.TINYINT -> ValueType.INTEGER;
            case Types.BIGINT -> ValueType.LONG;
            case Types.FLOAT, Types.REAL, Types.DOUBLE, Types.DECIMAL, Types.NUMERIC -> ValueType.DECIMAL;
            case Types.BOOLEAN, Types.BIT -> ValueType.BOOLEAN;
            case Types.DATE -> ValueType.DATE;
            case Types.TIME, Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> ValueType.DATETIME;
            default -> ValueType.STRING;
        };
    }

    protected FieldSemanticType toSemanticType(String name, ValueType valueType) {
        String lower = name == null ? "" : name.toLowerCase();
        if (lower.endsWith("_at") || lower.contains("time") || lower.contains("date")) {
            return FieldSemanticType.TIME_DIMENSION;
        }
        if (valueType == ValueType.INTEGER || valueType == ValueType.LONG || valueType == ValueType.DECIMAL) {
            return FieldSemanticType.METRIC;
        }
        return FieldSemanticType.DIMENSION;
    }
}
