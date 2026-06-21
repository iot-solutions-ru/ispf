package com.ispf.server.migration;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.server.application.data.ApplicationSchemaSession;
import com.ispf.server.application.data.ApplicationSchemaSupport;
import com.ispf.server.datasource.DataSourcePathResolver;
import com.ispf.server.object.ObjectManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
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
    private final ModelRegistry modelRegistry;
    private final ModelEngine modelEngine;
    private final ApplicationSchemaSession schemaSession;
    private final DataSourcePathResolver dataSourcePathResolver;
    private final JdbcTemplate jdbcTemplate;

    public MigrationObjectService(
            ObjectManager objectManager,
            ModelRegistry modelRegistry,
            ModelEngine modelEngine,
            ApplicationSchemaSession schemaSession,
            DataSourcePathResolver dataSourcePathResolver,
            JdbcTemplate jdbcTemplate
    ) {
        this.objectManager = objectManager;
        this.modelRegistry = modelRegistry;
        this.modelEngine = modelEngine;
        this.schemaSession = schemaSession;
        this.dataSourcePathResolver = dataSourcePathResolver;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void ensureCatalog() {
        if (objectManager.tree().findByPath(MIGRATIONS_ROOT).isEmpty()) {
            objectManager.create(
                    "root.platform",
                    "migrations",
                    ObjectType.MIGRATIONS,
                    "Migrations",
                    "SQL migrations applied on package import",
                    null
            );
        } else {
            objectManager.reconcileType(MIGRATIONS_ROOT, ObjectType.MIGRATIONS);
        }
    }

    @Transactional
    public void upsert(MigrationDefinition definition) {
        ensureCatalog();
        String nodeName = sanitizeNodeName(definition.scriptId());
        String path = MIGRATIONS_ROOT + "." + nodeName;
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(
                    MIGRATIONS_ROOT,
                    nodeName,
                    ObjectType.MIGRATION,
                    definition.scriptId(),
                    "Migration " + definition.scriptId(),
                    "migration-v1"
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
        List<String> applied = new ArrayList<>();
        for (MigrationDefinition migration : listAll()) {
            if (version != null && !version.isBlank() && !version.equals(migration.version())) {
                continue;
            }
            if (isApplied(migration)) {
                continue;
            }
            applyOne(migration);
            applied.add(migration.scriptId());
        }
        return applied;
    }

    @Transactional
    public void applyOne(MigrationDefinition migration) {
        String checksum = checksum(migration.sql());
        ApplicationSchemaSupport.validateMigrationSql(migration.sql(), "");
        String schemaName = dataSourcePathResolver.resolveSchemaName(migration.dataSourcePath());
        schemaSession.runInSchema(schemaName, () -> {
            for (String statement : splitStatements(migration.sql())) {
                if (!statement.isBlank()) {
                    jdbcTemplate.execute(statement);
                }
            }
        });
        setString(migration.path(), "checksum", checksum);
        setString(migration.path(), "appliedAt", Instant.now().toString());
    }

    private boolean isApplied(MigrationDefinition migration) {
        PlatformObject node = objectManager.require(migration.path());
        String stored = readString(node, "checksum").orElse("");
        if (stored.isBlank()) {
            return false;
        }
        return stored.equals(checksum(migration.sql()));
    }

    private List<MigrationDefinition> listAll() {
        ensureCatalog();
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
        PlatformObject node = objectManager.require(path);
        if (node.getVariable("scriptId").isPresent()) {
            return;
        }
        modelRegistry.findByName("migration-v1").ifPresent(model -> {
            modelEngine.applyModel(model.id(), path);
            objectManager.persistNodeTree(path);
        });
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
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : sql.split("\n")) {
            if (line.trim().endsWith(";")) {
                current.append(line, 0, line.lastIndexOf(';')).append('\n');
                statements.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(line).append('\n');
            }
        }
        if (current.length() > 0) {
            statements.add(current.toString().trim());
        }
        return statements;
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
}
