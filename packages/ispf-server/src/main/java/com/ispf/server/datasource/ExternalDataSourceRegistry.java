package com.ispf.server.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

@Component
public class ExternalDataSourceRegistry {

    private final DataSourceConnectionResolver connectionResolver;
    private final Map<String, HikariDataSource> pools = new ConcurrentHashMap<>();

    public ExternalDataSourceRegistry(DataSourceConnectionResolver connectionResolver) {
        this.connectionResolver = connectionResolver;
    }

    public void evict(String dataSourcePath) {
        HikariDataSource removed = pools.remove(dataSourcePath);
        if (removed != null) {
            removed.close();
        }
    }

    public ConnectionTestResult testConnection(ExternalJdbcConfig config) {
        try (HikariDataSource probe = createPool("probe", config)) {
            JdbcTemplate template = new JdbcTemplate(probe);
            template.queryForObject("SELECT 1", Integer.class);
            return ConnectionTestResult.ok();
        } catch (Exception ex) {
            return ConnectionTestResult.failed(connectionErrorMessage(ex));
        }
    }

    public boolean testConnection(String dataSourcePath) {
        return testConnection(connectionResolver.resolveExternalConfig(dataSourcePath)).connected();
    }

    public void runWithTemplate(String dataSourcePath, Consumer<JdbcTemplate> action) {
        callWithTemplate(dataSourcePath, template -> {
            action.accept(template);
            return null;
        });
    }

    public <T> T callWithTemplate(String dataSourcePath, Function<JdbcTemplate, T> action) {
        HikariDataSource pool = pools.computeIfAbsent(dataSourcePath, path -> {
            ExternalJdbcConfig config = connectionResolver.resolveExternalConfig(path);
            return createPool(path, config);
        });
        return action.apply(new JdbcTemplate(pool));
    }

    private static String connectionErrorMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        if (message.contains("password authentication failed") || message.contains("FATAL: password")) {
            return "Authentication failed — check username and password";
        }
        if (message.contains("Connection refused")) {
            return "Connection refused — check host, port, and that the database is running";
        }
        return message;
    }

    private static HikariDataSource createPool(String poolName, ExternalJdbcConfig config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("ispf-ext-" + poolName);
        hikari.setJdbcUrl(config.jdbcUrl());
        hikari.setDriverClassName(config.driverClassName());
        hikari.setUsername(config.username());
        hikari.setPassword(config.password());
        hikari.setMaximumPoolSize(config.maximumPoolSize());
        hikari.setMinimumIdle(1);
        hikari.setConnectionTimeout(10_000);
        return new HikariDataSource(hikari);
    }
}
