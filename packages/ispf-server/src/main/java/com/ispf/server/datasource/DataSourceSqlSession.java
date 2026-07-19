package com.ispf.server.datasource;

import com.ispf.server.application.data.ApplicationSchemaSession;
import com.ispf.server.tenant.TenantLocalDataAccessGuard;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.Consumer;
import java.util.function.Function;

@Component
public class DataSourceSqlSession {

    private final DataSourceConnectionResolver connectionResolver;
    private final ApplicationSchemaSession schemaSession;
    private final ExternalDataSourceRegistry externalRegistry;
    private final JdbcTemplate platformJdbcTemplate;
    private final TenantLocalDataAccessGuard tenantLocalDataAccessGuard;

    public DataSourceSqlSession(
            DataSourceConnectionResolver connectionResolver,
            ApplicationSchemaSession schemaSession,
            ExternalDataSourceRegistry externalRegistry,
            JdbcTemplate platformJdbcTemplate,
            TenantLocalDataAccessGuard tenantLocalDataAccessGuard
    ) {
        this.connectionResolver = connectionResolver;
        this.schemaSession = schemaSession;
        this.externalRegistry = externalRegistry;
        this.platformJdbcTemplate = platformJdbcTemplate;
        this.tenantLocalDataAccessGuard = tenantLocalDataAccessGuard;
    }

    public void ensureWritable(String dataSourcePath) {
        tenantLocalDataAccessGuard.requireAllowedDataSourcePath(dataSourcePath);
        if (!connectionResolver.isExternal(dataSourcePath)) {
            schemaSession.ensureSchemaExists(connectionResolver.resolveSchemaName(dataSourcePath));
        }
    }

    public void runWithDataSource(String dataSourcePath, Consumer<JdbcTemplate> action) {
        callWithDataSource(dataSourcePath, template -> {
            action.accept(template);
            return null;
        });
    }

    public <T> T callWithDataSource(String dataSourcePath, Function<JdbcTemplate, T> action) {
        tenantLocalDataAccessGuard.requireAllowedDataSourcePath(dataSourcePath);
        if (connectionResolver.isExternal(dataSourcePath)) {
            ExternalJdbcConfig config = connectionResolver.resolveExternalConfig(dataSourcePath);
            tenantLocalDataAccessGuard.requireAllowedJdbcUrl(config.jdbcUrl());
            return externalRegistry.callWithTemplate(dataSourcePath, action);
        }
        String schemaName = connectionResolver.resolveSchemaName(dataSourcePath);
        final Object[] holder = new Object[1];
        schemaSession.runInSchema(schemaName, () -> holder[0] = action.apply(platformJdbcTemplate));
        @SuppressWarnings("unchecked")
        T result = (T) holder[0];
        return result;
    }

    public ConnectionTestResult testConnection(String dataSourcePath) {
        return testConnection(dataSourcePath, null);
    }

    public ConnectionTestResult testConnection(
            String dataSourcePath,
            DataSourceConnectionResolver.ExternalConfigProbe probe
    ) {
        try {
            tenantLocalDataAccessGuard.requireAllowedDataSourcePath(dataSourcePath);
            if (connectionResolver.isExternal(dataSourcePath)) {
                ExternalJdbcConfig config = connectionResolver.resolveExternalConfig(dataSourcePath, probe);
                tenantLocalDataAccessGuard.requireAllowedJdbcUrl(config.jdbcUrl());
                return externalRegistry.testConnection(config);
            }
            Integer probeResult = callWithDataSource(dataSourcePath, template ->
                    template.queryForObject("SELECT 1", Integer.class));
            if (probeResult != null && probeResult == 1) {
                return ConnectionTestResult.ok();
            }
            return ConnectionTestResult.failed("Connection probe returned unexpected result");
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            return ConnectionTestResult.failed(connectionErrorMessage(ex));
        }
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
        return message;
    }
}
