package com.ispf.server.datasource;

import com.ispf.server.application.data.ApplicationSchemaSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;
import java.util.function.Function;

@Component
public class DataSourceSqlSession {

    private final DataSourceConnectionResolver connectionResolver;
    private final ApplicationSchemaSession schemaSession;
    private final ExternalDataSourceRegistry externalRegistry;
    private final JdbcTemplate platformJdbcTemplate;

    public DataSourceSqlSession(
            DataSourceConnectionResolver connectionResolver,
            ApplicationSchemaSession schemaSession,
            ExternalDataSourceRegistry externalRegistry,
            JdbcTemplate platformJdbcTemplate
    ) {
        this.connectionResolver = connectionResolver;
        this.schemaSession = schemaSession;
        this.externalRegistry = externalRegistry;
        this.platformJdbcTemplate = platformJdbcTemplate;
    }

    public void ensureWritable(String dataSourcePath) {
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
        if (connectionResolver.isExternal(dataSourcePath)) {
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
            if (connectionResolver.isExternal(dataSourcePath)) {
                ExternalJdbcConfig config = connectionResolver.resolveExternalConfig(dataSourcePath, probe);
                return externalRegistry.testConnection(config);
            }
            Integer probeResult = callWithDataSource(dataSourcePath, template ->
                    template.queryForObject("SELECT 1", Integer.class));
            if (probeResult != null && probeResult == 1) {
                return ConnectionTestResult.ok();
            }
            return ConnectionTestResult.failed("Connection probe returned unexpected result");
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
