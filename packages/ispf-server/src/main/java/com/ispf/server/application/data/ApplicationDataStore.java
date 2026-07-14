package com.ispf.server.application.data;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ApplicationDataStore {

    private final JdbcTemplate jdbcTemplate;
    private final PlatformSqlCatalog platformSqlCatalog;
    private final String applicationsTable;
    private final String migrationsTable;
    private final String seedsTable;

    public ApplicationDataStore(JdbcTemplate jdbcTemplate, PlatformSqlCatalog platformSqlCatalog) {
        this.jdbcTemplate = jdbcTemplate;
        this.platformSqlCatalog = platformSqlCatalog;
        this.applicationsTable = platformSqlCatalog.table("applications");
        this.migrationsTable = platformSqlCatalog.table("application_data_migrations");
        this.seedsTable = platformSqlCatalog.table("application_data_seeds");
    }

    public void registerApp(String appId, String displayName, String tablePrefix, String schemaName) {
        String resolvedSchema = schemaName != null && !schemaName.isBlank()
                ? schemaName
                : ApplicationSchemaSupport.defaultSchemaName(appId);

        if (findApp(appId).isPresent()) {
            jdbcTemplate.update("""
                    UPDATE %s
                    SET display_name = ?, table_prefix = ?, schema_name = ?
                    WHERE app_id = ?
                    """.formatted(applicationsTable),
                    displayName,
                    tablePrefix,
                    resolvedSchema,
                    appId
            );
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO %s (app_id, display_name, table_prefix, schema_name, created_at)
                VALUES (?, ?, ?, ?, ?)
                """.formatted(applicationsTable),
                appId,
                displayName,
                tablePrefix,
                resolvedSchema,
                Timestamp.from(Instant.now())
        );
    }

    public List<Map<String, Object>> listAllApps() {
        return jdbcTemplate.queryForList(
                "SELECT app_id, display_name, table_prefix, schema_name, created_at FROM "
                        + applicationsTable + " ORDER BY app_id"
        );
    }

    public Optional<Map<String, Object>> findApp(String appId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT app_id, display_name, table_prefix, schema_name, created_at FROM "
                        + applicationsTable + " WHERE app_id = ?",
                appId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public boolean deleteApp(String appId) {
        if (appId == null || appId.isBlank()) {
            return false;
        }
        String id = appId.trim();
        // Child tables reference applications(app_id) without ON DELETE CASCADE.
        for (String table : List.of(
                "application_bundle_deployments",
                "application_data_migrations",
                "application_data_seeds",
                "application_functions",
                "application_sql_bindings",
                "application_reports",
                "application_event_catalog"
        )) {
            jdbcTemplate.update(
                    "DELETE FROM " + platformSqlCatalog.table(table) + " WHERE app_id = ?",
                    id
            );
        }
        // App-scoped tables without FK (or nullable app_id).
        jdbcTemplate.update(
                "DELETE FROM " + platformSqlCatalog.table("operator_app_ui") + " WHERE app_id = ?",
                id
        );
        jdbcTemplate.update(
                "DELETE FROM " + platformSqlCatalog.table("platform_schedules") + " WHERE app_id = ?",
                id
        );
        int deleted = jdbcTemplate.update(
                "DELETE FROM " + applicationsTable + " WHERE app_id = ?",
                id
        );
        return deleted > 0;
    }

    public boolean isMigrationApplied(String appId, String version, String scriptId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM %s
                WHERE app_id = ? AND version = ? AND script_id = ?
                """.formatted(migrationsTable),
                Integer.class,
                appId,
                version,
                scriptId
        );
        return count != null && count > 0;
    }

    public void recordMigration(String appId, String version, String scriptId, String sql) {
        jdbcTemplate.update("""
                INSERT INTO %s (id, app_id, version, script_id, checksum, applied_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """.formatted(migrationsTable),
                UUID.randomUUID(),
                appId,
                version,
                scriptId,
                checksum(sql),
                Timestamp.from(Instant.now())
        );
    }

    public List<Map<String, Object>> listMigrations(String appId) {
        return jdbcTemplate.queryForList("""
                SELECT version, script_id, checksum, applied_at
                FROM %s
                WHERE app_id = ?
                ORDER BY applied_at
                """.formatted(migrationsTable),
                appId
        );
    }

    public void executeSql(String sql) {
        jdbcTemplate.execute(sql);
    }

    public List<Map<String, Object>> queryForList(String sql) {
        ApplicationSchemaSupport.validateSelectQuery(sql, "SQL query");
        return jdbcTemplate.queryForList(sql);
    }

    public boolean isSeedApplied(String appId, String profile, String seedId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM %s
                WHERE app_id = ? AND profile = ? AND seed_id = ?
                """.formatted(seedsTable),
                Integer.class,
                appId,
                profile,
                seedId
        );
        return count != null && count > 0;
    }

    public void recordSeed(String appId, String profile, String seedId) {
        jdbcTemplate.update("""
                INSERT INTO %s (id, app_id, profile, seed_id, applied_at)
                VALUES (?, ?, ?, ?, ?)
                """.formatted(seedsTable),
                UUID.randomUUID(),
                appId,
                profile,
                seedId,
                Timestamp.from(Instant.now())
        );
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
}
