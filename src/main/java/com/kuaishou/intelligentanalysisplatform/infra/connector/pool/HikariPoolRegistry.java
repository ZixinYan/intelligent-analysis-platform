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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HikariPoolRegistry {
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
        if (datasource.getJdbcOptions() != null) {
            datasource.getJdbcOptions().forEach(config::addDataSourceProperty);
        }
        return new HikariDataSource(config);
    }

    private String resolvePassword(AnalysisDatasource datasource) {
        String encryptedPassword = datasource.getEncryptedPassword();
        if (encryptedPassword == null || encryptedPassword.isBlank()) {
            return null;
        }
        return credentialEncryptor.decrypt(encryptedPassword);
    }

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
