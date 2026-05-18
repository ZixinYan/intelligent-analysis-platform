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

/**
 * JDBC 连接器基类，封装分页查询、Schema 推断、连接健康检查等通用逻辑。
 *
 * <p>设计决策：
 * <ul>
 *   <li><b>连接池复用</b>：通过 {@link HikariPoolRegistry} 按数据源 ID 懒初始化 HikariCP 连接池，
 *       避免每次查询创建新连接，同时支持连接池参数的差异化配置。</li>
 *   <li><b>查询取消</b>：执行前向 {@link QueryCancellationRegistry} 注册 Statement，
 *       外部调用 cancel(queryId) 时可直接调用 Statement.cancel() 中断 JDBC 执行。</li>
 *   <li><b>分页策略</b>：默认使用 OFFSET 分页，支持游标（Base64 编码的 offset 值）分页；
 *       子类可通过覆写 {@link #buildPaginatedSql} 适配特定数据库方言（如 ClickHouse 的 LIMIT N OFFSET M）。</li>
 *   <li><b>字段语义推断</b>：{@link #toSemanticType} 根据字段名称和类型启发式判断语义类型
 *       （时间维度 / 指标 / 维度），供前端图表推荐使用。</li>
 * </ul>
 *
 * <p>子类必须实现 {@link #buildListTablesSql} 提供数据库特定的表列表查询 SQL。
 */
public abstract class AbstractJdbcConnector implements Connector {
    private final HikariPoolRegistry poolRegistry;
    private final QueryCancellationRegistry cancellationRegistry;

    protected AbstractJdbcConnector(HikariPoolRegistry poolRegistry, QueryCancellationRegistry cancellationRegistry) {
        this.poolRegistry = poolRegistry;
        this.cancellationRegistry = cancellationRegistry;
    }

    /**
     * 执行分页 SQL 查询并返回结果集。
     *
     * <p>截断策略：读取行数达到 maxRows 时停止读取，结果中 {@code truncated=true} 标记被截断，
     * 前端收到该标志后可提示用户数据不完整。
     *
     * <p>游标：CURSOR 分页模式下，若当前页满则生成下一页游标（Base64 编码的 nextOffset），
     * 前端将其传回 option.cursor 实现翻页。
     *
     * @throws IllegalStateException JDBC 执行失败时包装 {@link SQLException} 抛出
     */
    @Override
    public QueryResult execute(AnalysisDatasource datasource, QueryCommand command) {
        long start = System.currentTimeMillis();
        DataSource dataSource = poolRegistry.getOrCreate(datasource);
        String sql = buildPaginatedSql(command);
        int effectiveOffset = resolveOffset(command);
        int effectivePageSize = resolvePageSize(command);
        int maxRows = resolveMaxRows(command, effectivePageSize);
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
                return QueryResult.builder()
                        .fields(fields)
                        .rows(rows)
                        .rowCount(rows.size())
                        .truncated(truncated)
                        .nextCursor(buildNextCursor(command, effectiveOffset, effectivePageSize, rows.size(), truncated))
                        .elapsedMs(System.currentTimeMillis() - start)
                        .cached(false)
                        .build();
            } finally {
                cancellationRegistry.deregister(command.getQueryId());
            }
        } catch (SQLException e) {
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

    /**
     * Build the native SQL query for listing tables.
     * Subclasses MUST override this to provide database-specific SQL.
     */
    protected abstract String buildListTablesSql(AnalysisDatasource datasource);

    @Override
    public HealthCheckResult healthCheck(AnalysisDatasource datasource) {
        long start = System.currentTimeMillis();
        DataSource dataSource = poolRegistry.getOrCreate(datasource);
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(3)) {
                return new HealthCheckResult(false, System.currentTimeMillis() - start, null, "connection invalid");
            }
            String serverVersion = connection.getMetaData().getDatabaseProductVersion();
            return new HealthCheckResult(true, System.currentTimeMillis() - start, serverVersion, "connection ok");
        } catch (SQLException e) {
            return new HealthCheckResult(false, System.currentTimeMillis() - start, null, e.getMessage());
        }
    }

    /**
     * 推断 SQL 语句的输出 Schema（字段名、类型、语义类型）。
     *
     * <p>实现原理：使用 {@code SELECT * FROM (...) t LIMIT 0} 包装原始 SQL，
     * 通过 JDBC ResultSetMetaData 获取列元数据，不实际传输数据，性能损耗极低。
     */
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

    /**
     * 构建下一页游标（CURSOR 分页模式）。
     *
     * <p>游标格式：Base64(nextOffset)，其中 nextOffset = offset + 当前页行数。
     * 末页（未截断且行数不满一页）返回 null，表示无更多数据。
     */
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

    /**
     * 将 JDBC 标准类型码（{@link java.sql.Types}）映射为平台统一的 {@link ValueType}。
     * 未识别的类型默认映射为 STRING，保证兼容性。
     */
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

    /**
     * 基于字段名称和数据类型启发式推断语义类型，供 AI 图表推荐和前端字段选择器使用。
     *
     * <p>规则（按优先级）：
     * <ol>
     *   <li>字段名包含 "_at"、"time"、"date" → {@code TIME_DIMENSION}</li>
     *   <li>数值类型（INTEGER / LONG / DECIMAL） → {@code METRIC}</li>
     *   <li>其余 → {@code DIMENSION}</li>
     * </ol>
     */
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
