package com.ispf.server.datasource;

import org.springframework.stereotype.Component;

@Component
public class DataSourcePathResolver {

    public static final String DATA_SOURCES_ROOT = "root.platform.data-sources";

    private final DataSourceConnectionResolver connectionResolver;

    public DataSourcePathResolver(DataSourceConnectionResolver connectionResolver) {
        this.connectionResolver = connectionResolver;
    }

    public boolean isExternal(String dataSourcePath) {
        return connectionResolver.isExternal(dataSourcePath);
    }

    public String resolveSchemaName(String dataSourcePath) {
        return connectionResolver.resolveSchemaName(dataSourcePath);
    }

    /**
     * @return schema name, or {@code null} when the report uses an external JDBC data source
     */
    public String resolveSchemaForReport(String dataSourcePath, String legacyAppId) {
        return connectionResolver.resolveSchemaForReport(dataSourcePath, legacyAppId);
    }

    public static String sanitizeNodeName(String name) {
        if (name == null || name.isBlank()) {
            return "node";
        }
        String sanitized = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (sanitized.isEmpty()) {
            return "node";
        }
        if (Character.isDigit(sanitized.charAt(0))) {
            return "n_" + sanitized;
        }
        return sanitized;
    }
}
