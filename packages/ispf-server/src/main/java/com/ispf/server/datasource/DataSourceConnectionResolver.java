package com.ispf.server.datasource;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.application.data.ApplicationSchemaSupport;
import com.ispf.server.config.ExternalDataSourceProperties;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DataSourceConnectionResolver {

    public static final String MODE_INTERNAL = "internal";
    public static final String MODE_EXTERNAL = "external";

    private final ObjectManager objectManager;
    private final ApplicationDataStore legacyAppStore;
    private final ExternalDataSourceProperties externalProperties;

    public DataSourceConnectionResolver(
            ObjectManager objectManager,
            ApplicationDataStore legacyAppStore,
            ExternalDataSourceProperties externalProperties
    ) {
        this.objectManager = objectManager;
        this.legacyAppStore = legacyAppStore;
        this.externalProperties = externalProperties;
    }

    public boolean isExternal(String dataSourcePath) {
        return MODE_EXTERNAL.equalsIgnoreCase(readString(dataSourcePath, "connectionMode").orElse(MODE_INTERNAL));
    }

    public String resolveSchemaName(String dataSourcePath) {
        if (isExternal(dataSourcePath)) {
            throw new IllegalArgumentException("Data source is external JDBC, not internal schema: " + dataSourcePath);
        }
        if (dataSourcePath == null || dataSourcePath.isBlank()) {
            throw new IllegalArgumentException(
                    "dataSourcePath is required — set root.platform.data-sources.* on the object"
            );
        }
        PlatformObject node = objectManager.require(dataSourcePath);
        if (node.type() != ObjectType.DATA_SOURCE) {
            throw new IllegalArgumentException("Not a data source object: " + dataSourcePath);
        }
        return readString(node, "schemaName")
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Data source schemaName is empty: " + dataSourcePath
                ));
    }

    public String resolveSchemaForReport(String dataSourcePath, String legacyAppId) {
        if (dataSourcePath != null && !dataSourcePath.isBlank()) {
            if (objectManager.tree().findByPath(dataSourcePath).isPresent()) {
                if (isExternal(dataSourcePath)) {
                    return null;
                }
                return resolveSchemaName(dataSourcePath);
            }
        }
        if (legacyAppId != null && !legacyAppId.isBlank()) {
            String inferredPath = DataSourcePathResolver.DATA_SOURCES_ROOT + "."
                    + DataSourcePathResolver.sanitizeNodeName(legacyAppId);
            if (objectManager.tree().findByPath(inferredPath).isPresent()) {
                if (isExternal(inferredPath)) {
                    return null;
                }
                return resolveSchemaName(inferredPath);
            }
            return legacyAppStore.findApp(legacyAppId)
                    .map(row -> String.valueOf(row.get("schema_name")))
                    .filter(s -> s != null && !s.isBlank() && !"null".equals(s))
                    .orElseGet(() -> ApplicationSchemaSupport.defaultSchemaName(legacyAppId));
        }
        throw new IllegalArgumentException("Report dataSourcePath or legacy appId is required");
    }

    public ExternalJdbcConfig resolveExternalConfig(String dataSourcePath) {
        return resolveExternalConfig(dataSourcePath, null);
    }

    public ExternalJdbcConfig resolveExternalConfig(String dataSourcePath, ExternalConfigProbe probe) {
        objectManager.require(dataSourcePath);
        String jdbcUrl = firstNonBlank(probe != null ? probe.jdbcUrl() : null,
                readString(dataSourcePath, "jdbcUrl").orElse(null));
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("jdbcUrl is required for external data source: " + dataSourcePath);
        }
        String driverClass = firstNonBlank(
                probe != null ? probe.jdbcDriverClass() : null,
                readString(dataSourcePath, "jdbcDriverClass").orElse(""));
        if (driverClass.isBlank()) {
            driverClass = inferDriverClass(jdbcUrl);
        }
        validateDriverClass(driverClass);
        int poolSize = probe != null && probe.poolSize() != null
                ? probe.poolSize()
                : readInteger(dataSourcePath, "poolSize").orElse(externalProperties.getDefaultPoolSize());
        poolSize = Math.min(Math.max(poolSize, 1), externalProperties.getMaxPoolSize());
        String username = firstNonBlank(
                probe != null ? probe.jdbcUsername() : null,
                readString(dataSourcePath, "jdbcUsername").orElse(""));
        String password = probe != null && probe.jdbcPassword() != null && !probe.jdbcPassword().isBlank()
                ? probe.jdbcPassword()
                : readString(dataSourcePath, "jdbcPassword").orElse("");
        return new ExternalJdbcConfig(jdbcUrl, driverClass, username, password, poolSize);
    }

    public record ExternalConfigProbe(
            String jdbcUrl,
            String jdbcDriverClass,
            String jdbcUsername,
            String jdbcPassword,
            Integer poolSize
    ) {
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void validateDriverClass(String driverClass) {
        boolean allowed = externalProperties.getAllowedDriverPrefixes().stream()
                .anyMatch(driverClass::startsWith);
        if (!allowed) {
            throw new IllegalArgumentException("JDBC driver not allowed: " + driverClass);
        }
    }

    private static String inferDriverClass(String jdbcUrl) {
        String url = jdbcUrl.toLowerCase();
        if (url.startsWith("jdbc:postgresql:")) {
            return "org.postgresql.Driver";
        }
        if (url.startsWith("jdbc:sqlserver:")) {
            return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        }
        if (url.startsWith("jdbc:mysql:")) {
            return "com.mysql.cj.jdbc.Driver";
        }
        if (url.startsWith("jdbc:mariadb:")) {
            return "org.mariadb.jdbc.Driver";
        }
        if (url.startsWith("jdbc:oracle:")) {
            return "oracle.jdbc.OracleDriver";
        }
        if (url.startsWith("jdbc:h2:")) {
            return "org.h2.Driver";
        }
        throw new IllegalArgumentException("Cannot infer JDBC driver for URL: " + jdbcUrl);
    }

    private String requireString(String path, String variable) {
        return readString(path, variable)
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new IllegalArgumentException(variable + " is required for external data source: " + path));
    }

    private Optional<String> readString(String path, String variableName) {
        return objectManager.tree().findByPath(path)
                .flatMap(node -> readString(node, variableName));
    }

    private Optional<Integer> readInteger(String path, String variableName) {
        return readString(path, variableName)
                .flatMap(value -> {
                    try {
                        return Optional.of(Integer.parseInt(value.trim()));
                    } catch (NumberFormatException ex) {
                        return Optional.empty();
                    }
                });
    }

    private static Optional<String> readString(PlatformObject node, String variableName) {
        return node.getVariable(variableName)
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString);
    }
}
