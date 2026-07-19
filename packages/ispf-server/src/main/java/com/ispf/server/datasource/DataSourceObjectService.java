package com.ispf.server.datasource;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.bootstrap.SystemObjectCatalogSupport;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.blueprint.SystemObjectStructureService;
import com.ispf.server.tenant.TenantLocalDataAccessGuard;
import com.ispf.server.tenant.TenantVirtualRootService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class DataSourceObjectService {

    private static final DataSchema STRING_SCHEMA = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private static final DataSchema INTEGER_SCHEMA = DataSchema.builder("integerValue")
            .field("value", FieldType.INTEGER)
            .build();

    private final ObjectManager objectManager;
    private final SystemObjectStructureService structureService;
    private final DataSourceSqlSession dataSourceSqlSession;
    private final ExternalDataSourceRegistry externalRegistry;
    private final DataSourceQueryExecutor queryExecutor;
    private final TenantLocalDataAccessGuard tenantLocalDataAccessGuard;
    private final TenantVirtualRootService tenantVirtualRootService;

    public DataSourceObjectService(
            ObjectManager objectManager,
            SystemObjectStructureService structureService,
            DataSourceSqlSession dataSourceSqlSession,
            ExternalDataSourceRegistry externalRegistry,
            DataSourceQueryExecutor queryExecutor,
            TenantLocalDataAccessGuard tenantLocalDataAccessGuard,
            TenantVirtualRootService tenantVirtualRootService
    ) {
        this.objectManager = objectManager;
        this.structureService = structureService;
        this.dataSourceSqlSession = dataSourceSqlSession;
        this.externalRegistry = externalRegistry;
        this.queryExecutor = queryExecutor;
        this.tenantLocalDataAccessGuard = tenantLocalDataAccessGuard;
        this.tenantVirtualRootService = tenantVirtualRootService;
    }

    @Transactional
    public void ensureCatalog() {
        ensureCatalogAt(DataSourcePathResolver.DATA_SOURCES_ROOT);
    }

    @Transactional
    public void ensureCatalogAt(String dataSourcesRoot) {
        SystemObjectCatalogSupport.ensureFolder(
                objectManager,
                dataSourcesRoot,
                ObjectType.DATA_SOURCES,
                null
        );
    }

    @Transactional
    public void ensureDataSource(String nodeName, String displayName, String schemaName, String description) {
        ensureDataSource(nodeName, displayName, DataSourceConnectionResolver.MODE_INTERNAL, schemaName,
                null, null, null, null, null, description);
    }

    @Transactional
    public void ensureDataSource(
            String nodeName,
            String displayName,
            String connectionMode,
            String schemaName,
            String jdbcUrl,
            String jdbcDriverClass,
            String jdbcUsername,
            String jdbcPassword,
            Integer poolSize,
            String description
    ) {
        String root = resolveDataSourcesRootForCaller();
        ensureCatalogAt(root);
        String path = root + "." + DataSourcePathResolver.sanitizeNodeName(nodeName);
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(
                    root,
                    DataSourcePathResolver.sanitizeNodeName(nodeName),
                    ObjectType.DATA_SOURCE,
                    displayName != null ? displayName : nodeName,
                    description != null ? description : "",
                    null
            );
        } else {
            objectManager.updateInfo(path, displayName != null ? displayName : nodeName, description != null ? description : "");
            objectManager.reconcileType(path, ObjectType.DATA_SOURCE);
        }
        ensureStructure(path);
        applyFields(path, displayName, connectionMode, schemaName, jdbcUrl, jdbcDriverClass,
                jdbcUsername, jdbcPassword, poolSize);
    }

    @Transactional
    public void ensureStructure(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.DATA_SOURCE) {
            throw new IllegalArgumentException("Not a data source object: " + path);
        }
        structureService.ensureDataSourceStructure(path);
    }

    public String pathForNodeName(String nodeName) {
        return resolveDataSourcesRootForCaller() + "." + DataSourcePathResolver.sanitizeNodeName(nodeName);
    }

    private String resolveDataSourcesRootForCaller() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return tenantVirtualRootService.dataSourcesRoot(auth);
    }

    @Transactional(readOnly = true)
    public DataSourceView getByPath(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.DATA_SOURCE) {
            throw new IllegalArgumentException("Not a data source object: " + path);
        }
        ensureStructure(path);
        return toView(path, node);
    }

    @Transactional
    public DataSourceView create(DataSourceWriteRequest request) {
        validateCreate(request);
        String path = pathForNodeName(request.name());
        enforceTenantDataSourceWrite(path, request);
        ensureDataSource(
                request.name(),
                request.displayName(),
                request.connectionMode(),
                request.schemaName(),
                request.jdbcUrl(),
                request.jdbcDriverClass(),
                request.jdbcUsername(),
                request.jdbcPassword(),
                request.poolSize(),
                request.description()
        );
        return getByPath(path);
    }

    @Transactional
    public DataSourceView update(String path, DataSourceWriteRequest request) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.DATA_SOURCE) {
            throw new IllegalArgumentException("Not a data source object: " + path);
        }
        enforceTenantDataSourceWrite(path, request);
        externalRegistry.evict(path);
        String nextDisplayName = request.displayName() != null && !request.displayName().isBlank()
                ? request.displayName() : node.displayName();
        String nextDescription = request.description() != null ? request.description() : node.description();
        objectManager.updateInfo(path, nextDisplayName, nextDescription);
        applyFields(
                path,
                nextDisplayName,
                request.connectionMode(),
                request.schemaName(),
                request.jdbcUrl(),
                request.jdbcDriverClass(),
                request.jdbcUsername(),
                request.jdbcPassword(),
                request.poolSize()
        );
        return getByPath(path);
    }

    public ConnectionTestResult testConnection(String path) {
        return testConnection(path, null);
    }

    public ConnectionTestResult testConnection(String path, DataSourceConnectionResolver.ExternalConfigProbe probe) {
        tenantLocalDataAccessGuard.requireAllowedDataSourcePath(path);
        if (probe != null && probe.jdbcUrl() != null && !probe.jdbcUrl().isBlank()) {
            tenantLocalDataAccessGuard.requireAllowedJdbcUrl(probe.jdbcUrl());
        }
        return dataSourceSqlSession.testConnection(path, probe);
    }

    public DataSourceQueryResult executeQuery(String path, String query, List<Object> params, Integer maxRows) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.DATA_SOURCE) {
            throw new IllegalArgumentException("Not a data source object: " + path);
        }
        ensureStructure(path);
        tenantLocalDataAccessGuard.requireAllowedDataSourcePath(path);
        return queryExecutor.execute(path, query, params, maxRows);
    }

    private void enforceTenantDataSourceWrite(String path, DataSourceWriteRequest request) {
        if (!tenantLocalDataAccessGuard.isTenantCaller()) {
            return;
        }
        if (request.connectionMode() != null) {
            tenantLocalDataAccessGuard.requireExternalConnectionMode(request.connectionMode());
            if (DataSourceConnectionResolver.MODE_EXTERNAL.equalsIgnoreCase(request.connectionMode())) {
                tenantLocalDataAccessGuard.requireAllowedJdbcUrl(request.jdbcUrl());
            }
            return;
        }
        // Mode omitted on update — existing object must already be an allowed external DS.
        tenantLocalDataAccessGuard.requireAllowedDataSourcePath(path);
        if (request.jdbcUrl() != null && !request.jdbcUrl().isBlank()) {
            tenantLocalDataAccessGuard.requireAllowedJdbcUrl(request.jdbcUrl());
        }
    }

    private void applyFields(
            String path,
            String displayName,
            String connectionMode,
            String schemaName,
            String jdbcUrl,
            String jdbcDriverClass,
            String jdbcUsername,
            String jdbcPassword,
            Integer poolSize
    ) {
        if (connectionMode != null) {
            setString(path, "connectionMode", connectionMode);
        }
        if (schemaName != null) {
            setString(path, "schemaName", schemaName);
        }
        if (jdbcUrl != null) {
            setString(path, "jdbcUrl", jdbcUrl);
        }
        if (jdbcDriverClass != null) {
            setString(path, "jdbcDriverClass", jdbcDriverClass);
        }
        if (jdbcUsername != null) {
            setString(path, "jdbcUsername", jdbcUsername);
        }
        if (jdbcPassword != null) {
            setString(path, "jdbcPassword", jdbcPassword);
        }
        if (poolSize != null) {
            setInteger(path, "poolSize", poolSize);
        }
    }

    private static void validateCreate(DataSourceWriteRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        String mode = request.connectionMode() != null ? request.connectionMode() : DataSourceConnectionResolver.MODE_INTERNAL;
        if (DataSourceConnectionResolver.MODE_EXTERNAL.equalsIgnoreCase(mode)) {
            if (request.jdbcUrl() == null || request.jdbcUrl().isBlank()) {
                throw new IllegalArgumentException("jdbcUrl is required for external data source");
            }
        } else if (request.schemaName() == null || request.schemaName().isBlank()) {
            throw new IllegalArgumentException("schemaName is required for internal data source");
        }
    }

    private DataSourceView toView(String path, PlatformObject node) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String responsePath = Optional.ofNullable(tenantVirtualRootService.toVirtual(path, auth)).orElse(path);
        return new DataSourceView(
                responsePath,
                node.displayName(),
                node.description(),
                readString(node, "displayName").orElse(node.displayName()),
                readString(node, "connectionMode").orElse(DataSourceConnectionResolver.MODE_INTERNAL),
                readSchemaName(node).orElse(""),
                readString(node, "jdbcUrl").orElse(""),
                readString(node, "jdbcDriverClass").orElse(""),
                readString(node, "jdbcUsername").orElse(""),
                readString(node, "jdbcPassword").map(ignored -> "********").orElse(""),
                readInteger(node, "poolSize").orElse(5)
        );
    }

    public Optional<String> readSchemaName(String path) {
        return objectManager.tree().findByPath(path).flatMap(this::readSchemaName);
    }

    private Optional<String> readSchemaName(PlatformObject node) {
        return readString(node, "schemaName").filter(s -> !s.isBlank());
    }

    private void setString(String path, String variable, String value) {
        objectManager.setVariableValue(
                path,
                variable,
                DataRecord.single(STRING_SCHEMA, java.util.Map.of("value", value != null ? value : ""))
        );
    }

    private void setInteger(String path, String variable, int value) {
        objectManager.setVariableValue(
                path,
                variable,
                DataRecord.single(INTEGER_SCHEMA, java.util.Map.of("value", value))
        );
    }

    private static Optional<String> readString(PlatformObject node, String name) {
        return node.getVariable(name)
                .flatMap(Variable::value)
                .map(record -> String.valueOf(record.firstRow().get("value")));
    }

    private static Optional<Integer> readInteger(PlatformObject node, String name) {
        return node.getVariable(name)
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(value -> value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value)));
    }

    public record DataSourceWriteRequest(
            String name,
            String displayName,
            String connectionMode,
            String schemaName,
            String jdbcUrl,
            String jdbcDriverClass,
            String jdbcUsername,
            String jdbcPassword,
            Integer poolSize,
            String description
    ) {
    }

    public record DataSourceView(
            String path,
            String displayName,
            String description,
            String variableDisplayName,
            String connectionMode,
            String schemaName,
            String jdbcUrl,
            String jdbcDriverClass,
            String jdbcUsername,
            String jdbcPassword,
            int poolSize
    ) {
    }
}
