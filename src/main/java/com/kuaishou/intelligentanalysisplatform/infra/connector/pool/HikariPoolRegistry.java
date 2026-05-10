package com.kuaishou.intelligentanalysisplatform.infra.connector.pool;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceType;
import com.kuaishou.intelligentanalysisplatform.domain.datasource.AnalysisDatasource;
import com.kuaishou.intelligentanalysisplatform.infra.security.CredentialEncryptor;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

@Component
public class HikariPoolRegistry {
    private static final Logger log = LoggerFactory.getLogger(HikariPoolRegistry.class);

    private final ConcurrentHashMap<String, HikariDataSource> pools = new ConcurrentHashMap<>();
    private final CredentialEncryptor credentialEncryptor;
    private final int maxPoolSize;
    private final long connectionTimeoutMs;
    private final long idleTimeoutMs;

    public HikariPoolRegistry(CredentialEncryptor credentialEncryptor,
                              @Value("${connector.pool.max-pool-size:5}") int maxPoolSize,
                              @Value("${connector.pool.connection-timeout-ms:3000}") long connectionTimeoutMs,
                              @Value("${connector.pool.idle-timeout-ms:600000}") long idleTimeoutMs) {
        this.credentialEncryptor = credentialEncryptor;
        this.maxPoolSize = maxPoolSize;
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.idleTimeoutMs = idleTimeoutMs;
    }

    public DataSource getOrCreate(AnalysisDatasource datasource) {
        return pools.computeIfAbsent(datasource.getId(), key -> createDataSource(datasource));
    }

    public void evict(String datasourceId) {
        HikariDataSource dataSource = pools.remove(datasourceId);
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private HikariDataSource createDataSource(AnalysisDatasource datasource) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("connector-" + datasource.getId());
        config.setJdbcUrl(buildJdbcUrl(datasource));
        config.setUsername(datasource.getUsername());
        config.setPassword(resolvePassword(datasource));
        config.setMaximumPoolSize(maxPoolSize);
        config.setConnectionTimeout(connectionTimeoutMs);
        config.setIdleTimeout(idleTimeoutMs);
        config.setInitializationFailTimeout(-1);
        config.setAutoCommit(true);

        // 为 MySQL 添加默认 JDBC 连接属性，防止 SSL/认证问题
        if (datasource.getType() == DatasourceType.MYSQL) {
            config.addDataSourceProperty("useSSL", "false");
            config.addDataSourceProperty("allowPublicKeyRetrieval", "true");
            config.addDataSourceProperty("serverTimezone", "UTC");
            config.setConnectionTestQuery("SELECT 1");
        }
        // 为 PostgreSQL 添加默认属性
        if (datasource.getType() == DatasourceType.POSTGRES) {
            config.addDataSourceProperty("connectTimeout", "10");
            config.setConnectionTestQuery("SELECT 1");
        }

        // 用户自定义 JDBC 参数可覆盖默认值
        if (datasource.getJdbcOptions() != null) {
            datasource.getJdbcOptions().forEach(config::addDataSourceProperty);
        }

        String jdbcUrl = buildJdbcUrl(datasource);
        log.debug("Creating HikariCP pool for datasource {}: url={}, user={}",
                datasource.getId(), jdbcUrl.replaceAll("://([^:]+):([^@]+)@", "://***:***@"), datasource.getUsername());

        HikariDataSource dataSource = new HikariDataSource(config);

        // 尝试立即获取一个连接以提前暴露连接问题
        try (java.sql.Connection conn = dataSource.getConnection()) {
            log.info("Connection pool for datasource {} validated successfully", datasource.getId());
        } catch (SQLException e) {
            log.error("Failed to validate connection for datasource {}: {}", datasource.getId(), e.getMessage());
            // 即使验证失败也不在创建时抛异常，让调用方通过 getConnection() 获取具体错误
        }

        return dataSource;
    }

    private String resolvePassword(AnalysisDatasource datasource) {
        String encryptedPassword = datasource.getEncryptedPassword();
        if (encryptedPassword == null || encryptedPassword.isBlank()) {
            return null;
        }
        return credentialEncryptor.decrypt(encryptedPassword);
    }

    /**
     * 用于创建数据库链接
     * @param datasource
     * @return
     */
    private String buildJdbcUrl(AnalysisDatasource datasource) {
        String database = datasource.getDatabase() == null ? "" : "/" + datasource.getDatabase();
        String queryString = buildQueryString(datasource.getJdbcOptions());
        return switch (datasource.getType()) {
            case MYSQL -> "jdbc:mysql://" + datasource.getHost() + ":" + datasource.getPort() + database + queryString;
            case POSTGRES -> "jdbc:postgresql://" + datasource.getHost() + ":" + datasource.getPort() + database + queryString;
            case CLICKHOUSE -> "jdbc:clickhouse://" + datasource.getHost() + ":" + datasource.getPort() + database + queryString;
        };
    }

    private String buildQueryString(Map<String, String> jdbcOptions) {
        if (jdbcOptions == null || jdbcOptions.isEmpty()) {
            return "";
        }
        return "?" + jdbcOptions.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }
}
