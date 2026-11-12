package com.ispf.server.migration;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.datasource.DataSourceSqlSession;
import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.application.data.ApplicationSchemaSupport;
import com.ispf.server.bootstrap.SystemObjectCatalogSupport;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.blueprint.SystemObjectStructureService;
import com.ispf.server.tenant.TenantLocalDataAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MigrationObjectService {

    public static final String MIGRATIONS_ROOT = "root.platform.migrations";

    private static final DataSchema STRING_SCHEMA = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private final ObjectManager objectManager;
    private final SystemObjectStructureService structureService;
    private final DataSourceSqlSession dataSourceSqlSession;
    private final TenantLocalDataAccessGuard tenantLocalDataAccessGuard;
    private final ApplicationDataStore applicationDataStore;

    public MigrationObjectService(
            ObjectManager objectManager,
            SystemObjectStructureService structureService,
            DataSourceSqlSession dataSourceSqlSession,
            TenantLocalDataAccessGuard tenantLocalDataAccessGuard,
            ApplicationDataStore applicationDataStore
    ) {
        this.objectManager = objectManager;
        this.structureService = structureService;
        this.dataSourceSqlSession = dataSourceSqlSession;
        this.tenantLocalDataAccessGuard = tenantLocalDataAccessGuard;
        this.applicationDataStore = applicationDataStore;
    }

    @Transactional
    public void upsert(MigrationDefinition definition) {
        tenantLocalDataAccessGuard.requireAllowedDataSourcePath(definition.dataSourcePath());
        ensureCatalogInternal();
        String nodeName = sanitizeNodeName(definition.scriptId());
        String path = MIGRATIONS_ROOT + "." + nodeName;
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(
                    MIGRATIONS_ROOT,
                    nodeName,
                    ObjectType.MIGRATION,
                    definition.scriptId(),
                    "Migration " + definition.scriptId(),
                    null
            );
        }
        ensureStructure(path);
        setString(path, "scriptId", definition.scriptId());
        setString(path, "version", definition.version());
        setString(path, "dataSourcePath", definition.dataSourcePath());
        setString(path, "sql", definition.sql());
    }

    @Transactional
    public List<String> applyPending(String version) {
        return applyPending(version, null);
    }

    /**
     * Apply pending migrations matching {@code version} and, when provided, {@code dataSourcePath}.
     * Without a data-source filter, all platform migrations for the version run (legacy behaviour).
     * Order is the object-tree child order.
     */
    @Transactional
    public List<String> applyPending(String version, String dataSourcePath) {
        return applyPending(version, dataSourcePath, List.of());
    }

    /**
     * Same filter as {@link #applyPending(String, String)}. When {@code scriptIdsInOrder} is
     * non-empty, those ids run first in that order (bundle manifest order). Any other pending
     * migration for the same filter follows in tree order. Tree child order is not the apply order.
     */
    @Transactional
    public List<String> applyPending(String version, String dataSourcePath, List<String> scriptIdsInOrder) {
        List<MigrationDefinition> pending = new ArrayList<>();
        for (MigrationDefinition migration : listAll()) {
            if (!matchesPendingFilter(migration, version, dataSourcePath)) {
                continue;
            }
            if (isApplied(migration)) {
                continue;
            }
            pending.add(migration);
        }
        List<String> applied = new ArrayList<>();
        for (MigrationDefinition migration : orderForApply(pending, scriptIdsInOrder)) {
            applyOne(migration);
            applied.add(migration.scriptId());
        }
        return applied;
    }

    /**
     * Executes bundle SQL and the application migration ledger on one connection.
     * Does not create migration objects. A failure rolls back every statement in the batch,
     * so an earlier {@code CREATE} cannot remain when a later statement fails.
     * Not {@code @Transactional}: {@code JdbcTemplate} was committing each statement on its own connection.
     */
    public List<String> applyBundleSql(
            String appId,
            String tablePrefix,
            List<MigrationDefinition> scripts
    ) {
        if (scripts == null || scripts.isEmpty()) {
            return List.of();
        }
        String prefix = tablePrefix != null ? tablePrefix : "";
        List<MigrationDefinition> pending = new ArrayList<>();
        String dataSourcePath = null;
        for (MigrationDefinition script : scripts) {
            if (script == null || script.scriptId() == null || script.scriptId().isBlank()) {
                throw new IllegalArgumentException("migration id is required");
            }
            if (script.sql() == null || script.sql().isBlank()) {
                throw new IllegalArgumentException("migration " + script.scriptId() + ": sql is required");
            }
            ApplicationSchemaSupport.validateMigrationSql(script.sql(), prefix);
            if (dataSourcePath == null) {
                dataSourcePath = script.dataSourcePath();
            } else if (!dataSourcePath.equals(script.dataSourcePath())) {
                throw new IllegalArgumentException("Bundle migrations must share one data source");
            }
            String stored = applicationDataStore.appliedMigrationChecksum(appId, script.scriptId());
            MigrationLedger.requireUnchanged(script.scriptId(), stored, script.sql());
            if (stored == null) {
                pending.add(script);
            }
        }
        if (pending.isEmpty()) {
            return List.of();
        }
        List<String> statements = new ArrayList<>();
        for (MigrationDefinition script : pending) {
            for (String statement : splitStatements(script.sql())) {
                if (!statement.isBlank()) {
                    statements.add(statement);
                }
            }
        }
        String path = dataSourcePath;
        dataSourceSqlSession.runInSchemaTransaction(path, (connection, schemaName) -> {
            List<String> createdTables = new ArrayList<>();
            try {
                // Operator-authored migration SQL, same contract as MigrationObjectService.applyOne.
                for (String statement : statements) {
                    List<String> newTables = new ArrayList<>();
                    for (String table : ApplicationSchemaSupport.createTableNames(statement)) {
                        if (!tableExists(connection, schemaName, table)) {
                            newTables.add(table);
                        }
                    }
                    try (java.sql.Statement jdbc = connection.createStatement()) {
                        // codeql[java/sql-injection]
                        jdbc.execute(statement);
                    }
                    createdTables.addAll(newTables);
                }
                for (MigrationDefinition script : pending) {
                    applicationDataStore.recordMigration(
                            connection,
                            appId,
                            script.version(),
                            script.scriptId(),
                            script.sql()
                    );
                }
            } catch (SQLException ex) {
                // Some engines keep CREATE TABLE after rollback. Drop only tables this batch created,
                // child tables first, and always name the application schema.
                try {
                    connection.rollback();
                } catch (SQLException rollbackEx) {
                    ex.addSuppressed(rollbackEx);
                }
                try {
                    String safeSchema = ApplicationSchemaSupport.quoteIdentifier(schemaName);
                    for (int index = createdTables.size() - 1; index >= 0; index--) {
                        String safeTable = ApplicationSchemaSupport.quoteIdentifier(createdTables.get(index));
                        try (java.sql.Statement jdbc = connection.createStatement()) {
                            // codeql[java/sql-injection]
                            jdbc.execute("DROP TABLE IF EXISTS " + safeSchema + "." + safeTable);
                        }
                    }
                    connection.commit();
                } catch (SQLException cleanupEx) {
                    try {
                        connection.rollback();
                    } catch (SQLException ignored) {
                        cleanupEx.addSuppressed(ignored);
                    }
                    ex.addSuppressed(cleanupEx);
                }
                throw ex;
            }
        });
        return pending.stream().map(MigrationDefinition::scriptId).toList();
    }

    private static boolean tableExists(Connection connection, String schemaName, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE LOWER(table_schema) = LOWER(?) AND LOWER(table_name) = LOWER(?)
                """)) {
            statement.setString(1, schemaName);
            statement.setString(2, table);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getInt(1) > 0;
            }
        }
    }

    /**
     * Writes the migration object after {@link #applyBundleSql} has committed.
     * The SQL batch stays free of object-tree writes so a rollback cannot leave a node in memory.
     */
    @Transactional
    public void recordBundleMigrationNode(MigrationDefinition definition) {
        upsert(definition);
        String path = MIGRATIONS_ROOT + "." + sanitizeNodeName(definition.scriptId());
        setString(path, "checksum", checksum(definition.sql()));
        setString(path, "appliedAt", Instant.now().toString());
    }

    /**
     * Manifest ids first, in the given order. Remaining pending scripts keep their incoming order.
     * An empty order list leaves the incoming order unchanged.
     */
    static List<MigrationDefinition> orderForApply(
            List<MigrationDefinition> pending,
            List<String> scriptIdsInOrder
    ) {
        if (pending == null || pending.isEmpty()) {
            return List.of();
        }
        if (scriptIdsInOrder == null || scriptIdsInOrder.isEmpty()) {
            return List.copyOf(pending);
        }
        Map<String, MigrationDefinition> byId = new LinkedHashMap<>();
        for (MigrationDefinition migration : pending) {
            if (migration == null || migration.scriptId() == null || migration.scriptId().isBlank()) {
                continue;
            }
            byId.putIfAbsent(migration.scriptId(), migration);
        }
        List<MigrationDefinition> ordered = new ArrayList<>();
        for (String scriptId : scriptIdsInOrder) {
            if (scriptId == null || scriptId.isBlank()) {
                continue;
            }
            MigrationDefinition migration = byId.remove(scriptId);
            if (migration != null) {
                ordered.add(migration);
            }
        }
        ordered.addAll(byId.values());
        return ordered;
    }

    static boolean matchesPendingFilter(MigrationDefinition migration, String version, String dataSourcePath) {
        if (migration == null) {
            return false;
        }
        if (version != null && !version.isBlank() && !version.equals(migration.version())) {
            return false;
        }
        if (dataSourcePath != null && !dataSourcePath.isBlank()
                && !dataSourcePath.equals(migration.dataSourcePath())) {
            return false;
        }
        return true;
    }

    @Transactional(readOnly = true)
    public MigrationView getByPath(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.MIGRATION) {
            throw new IllegalArgumentException("Not a migration object: " + path);
        }
        MigrationDefinition definition = toDefinition(path, node)
                .orElseThrow(() -> new IllegalArgumentException("Invalid migration: " + path));
        return toView(definition, node);
    }

    @Transactional
    public void applyByPath(String path) {
        applyOne(requireDefinition(path));
    }

    @Transactional
    public MigrationView create(String scriptId, String version, String dataSourcePath, String sql) {
        if (scriptId == null || scriptId.isBlank()) {
            throw new IllegalArgumentException("scriptId is required");
        }
        upsert(new MigrationDefinition("", scriptId, version != null ? version : "1.0.0", dataSourcePath, sql != null ? sql : ""));
        return getByPath(MIGRATIONS_ROOT + "." + sanitizeNodeName(scriptId));
    }

    @Transactional
    public MigrationView update(String path, String scriptId, String version, String dataSourcePath, String sql) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.MIGRATION) {
            throw new IllegalArgumentException("Not a migration object: " + path);
        }
        MigrationDefinition current = requireDefinition(path);
        upsert(new MigrationDefinition(
                path,
                scriptId != null && !scriptId.isBlank() ? scriptId : current.scriptId(),
                version != null ? version : current.version(),
                dataSourcePath != null ? dataSourcePath : current.dataSourcePath(),
                sql != null ? sql : current.sql()
        ));
        return getByPath(path);
    }

    @Transactional
    public void applyOne(MigrationDefinition migration) {
        tenantLocalDataAccessGuard.requireAllowedDataSourcePath(migration.dataSourcePath());
        String checksum = checksum(migration.sql());
        ApplicationSchemaSupport.validateMigrationSql(migration.sql(), "");
        dataSourceSqlSession.ensureWritable(migration.dataSourcePath());
        dataSourceSqlSession.runWithDataSource(migration.dataSourcePath(), jdbc -> {
            for (String statement : splitStatements(migration.sql())) {
                if (!statement.isBlank()) {
                    jdbc.execute(statement);
                }
            }
        });
        setString(migration.path(), "checksum", checksum);
        setString(migration.path(), "appliedAt", Instant.now().toString());
        recordAppliedMigration(migration);
    }

    void recordAppliedMigration(MigrationDefinition migration) {
        String appId = appIdFromDataSourcePath(migration.dataSourcePath());
        if (appId == null || applicationDataStore.findApp(appId).isEmpty()) {
            return;
        }
        if (applicationDataStore.isMigrationApplied(appId, migration.version(), migration.scriptId())) {
            return;
        }
        applicationDataStore.recordMigration(appId, migration.version(), migration.scriptId(), migration.sql());
    }

    private MigrationDefinition requireDefinition(String path) {
        PlatformObject node = objectManager.require(path);
        return toDefinition(path, node)
                .orElseThrow(() -> new IllegalArgumentException("Invalid migration: " + path));
    }

    private MigrationView toView(MigrationDefinition definition, PlatformObject node) {
        String checksum = readString(node, "checksum").orElse("");
        String appliedAt = readString(node, "appliedAt").orElse("");
        return new MigrationView(
                definition.path(),
                definition.scriptId(),
                definition.version(),
                definition.dataSourcePath(),
                definition.sql(),
                checksum,
                appliedAt,
                isApplied(definition)
        );
    }

    private boolean isApplied(MigrationDefinition migration) {
        String appId = appIdFromDataSourcePath(migration.dataSourcePath());
        if (appId != null
                && applicationDataStore.isMigrationApplied(appId, migration.version(), migration.scriptId())) {
            return true;
        }
        PlatformObject node = objectManager.require(migration.path());
        String stored = readString(node, "checksum").orElse("");
        if (stored.isBlank()) {
            return false;
        }
        return stored.equals(checksum(migration.sql()));
    }

    static String appIdFromDataSourcePath(String dataSourcePath) {
        if (dataSourcePath == null || dataSourcePath.isBlank()) {
            return null;
        }
        int lastDot = dataSourcePath.lastIndexOf('.');
        String node = lastDot < 0 ? dataSourcePath : dataSourcePath.substring(lastDot + 1);
        return node.isBlank() ? null : node;
    }

    private List<MigrationDefinition> listAll() {
        ensureCatalogInternal();
        List<MigrationDefinition> migrations = new ArrayList<>();
        if (objectManager.tree().findByPath(MIGRATIONS_ROOT).isEmpty()) {
            return migrations;
        }
        for (PlatformObject child : objectManager.tree().childrenOf(MIGRATIONS_ROOT)) {
            if (child.type() != ObjectType.MIGRATION) {
                continue;
            }
            toDefinition(child.path(), child).ifPresent(migrations::add);
        }
        return migrations;
    }

    private void ensureStructure(String path) {
        structureService.ensureMigrationStructure(path);
    }

    @Transactional
    public void ensureCatalog() {
        ensureCatalogInternal();
    }

    private void ensureCatalogInternal() {
        SystemObjectCatalogSupport.ensureFolder(objectManager, MIGRATIONS_ROOT, ObjectType.MIGRATIONS, null);
    }

    private Optional<MigrationDefinition> toDefinition(String path, PlatformObject node) {
        return Optional.of(new MigrationDefinition(
                path,
                readString(node, "scriptId").orElse(path.substring(path.lastIndexOf('.') + 1)),
                readString(node, "version").orElse("1.0.0"),
                readString(node, "dataSourcePath").orElse(""),
                readString(node, "sql").orElse("")
        ));
    }

    public static String sanitizeNodeName(String name) {
        if (name == null || name.isBlank()) {
            return "migration";
        }
        String sanitized = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        return sanitized.isEmpty() ? "migration" : sanitized;
    }

    private static String checksum(String sql) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sql.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Checksum failed", ex);
        }
    }

    private static List<String> splitStatements(String sql) {
        return SqlStatementSplitter.split(sql);
    }

    private void setString(String path, String variable, String value) {
        objectManager.setVariableValue(path, variable, DataRecord.single(STRING_SCHEMA, Map.of("value", value != null ? value : "")));
    }

    private static Optional<String> readString(PlatformObject node, String name) {
        return node.getVariable(name).flatMap(Variable::value).map(r -> String.valueOf(r.firstRow().get("value")));
    }

    public record MigrationDefinition(
            String path,
            String scriptId,
            String version,
            String dataSourcePath,
            String sql
    ) {
    }

    public record MigrationView(
            String path,
            String scriptId,
            String version,
            String dataSourcePath,
            String sql,
            String checksum,
            String appliedAt,
            boolean applied
    ) {
    }
}
