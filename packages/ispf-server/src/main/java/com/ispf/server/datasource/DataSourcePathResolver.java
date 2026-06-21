package com.ispf.server.datasource;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.application.data.ApplicationSchemaSupport;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DataSourcePathResolver {

    public static final String DATA_SOURCES_ROOT = "root.platform.data-sources";

    private final ObjectManager objectManager;
    private final ApplicationDataStore legacyAppStore;

    public DataSourcePathResolver(ObjectManager objectManager, ApplicationDataStore legacyAppStore) {
        this.objectManager = objectManager;
        this.legacyAppStore = legacyAppStore;
    }

    public String resolveSchemaName(String dataSourcePath) {
        if (dataSourcePath == null || dataSourcePath.isBlank()) {
            throw new IllegalArgumentException(
                    "dataSourcePath is required — set root.platform.data-sources.* on the object"
            );
        }
        PlatformObject node = objectManager.require(dataSourcePath);
        if (node.type() != ObjectType.DATA_SOURCE) {
            throw new IllegalArgumentException("Not a data source object: " + dataSourcePath);
        }
        String schemaName = readString(node, "schemaName")
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Data source schemaName is empty: " + dataSourcePath
                ));
        return schemaName;
    }

    /**
     * Resolves schema for reports/bindings/functions. Supports legacy {@code appId} by mapping to
     * {@code root.platform.data-sources.{appId}} or registered application schema.
     */
    public String resolveSchemaForReport(String dataSourcePath, String legacyAppId) {
        if (dataSourcePath != null && !dataSourcePath.isBlank()) {
            if (objectManager.tree().findByPath(dataSourcePath).isPresent()) {
                return resolveSchemaName(dataSourcePath);
            }
        }
        if (legacyAppId != null && !legacyAppId.isBlank()) {
            String inferredPath = DATA_SOURCES_ROOT + "." + sanitizeNodeName(legacyAppId);
            if (objectManager.tree().findByPath(inferredPath).isPresent()) {
                return resolveSchemaName(inferredPath);
            }
            return legacyAppStore.findApp(legacyAppId)
                    .map(row -> String.valueOf(row.get("schema_name")))
                    .filter(s -> s != null && !s.isBlank() && !"null".equals(s))
                    .orElseGet(() -> ApplicationSchemaSupport.defaultSchemaName(legacyAppId));
        }
        throw new IllegalArgumentException(
                "Report dataSourcePath or legacy appId is required"
        );
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

    private static Optional<String> readString(PlatformObject node, String variableName) {
        return node.getVariable(variableName)
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString);
    }
}
