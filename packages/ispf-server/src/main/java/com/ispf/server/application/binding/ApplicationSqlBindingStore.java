package com.ispf.server.application.binding;

import com.ispf.server.application.data.PlatformSqlCatalog;
import com.ispf.server.relational.RelationalDialect;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ApplicationSqlBindingStore {

    private final JdbcTemplate jdbcTemplate;
    private final String bindingsTable;
    private final RelationalDialect relationalDialect;

    public ApplicationSqlBindingStore(
            JdbcTemplate jdbcTemplate,
            PlatformSqlCatalog platformSqlCatalog,
            RelationalDialect relationalDialect
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.bindingsTable = platformSqlCatalog.table("application_sql_bindings");
        this.relationalDialect = relationalDialect;
    }

    public void upsert(SqlBinding binding) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM %s
                WHERE app_id = ? AND object_path = ? AND variable_name = ?
                """.formatted(bindingsTable),
                Integer.class,
                binding.appId(),
                binding.objectPath(),
                binding.variableName()
        );
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                    UPDATE %s
                    SET query_sql = ?, refresh_mode = ?, refresh_interval_ms = ?, value_field = ?,
                        trigger_object_path = ?, trigger_function_name = ?, enabled = ?, deployed_at = ?
                    WHERE app_id = ? AND object_path = ? AND variable_name = ?
                    """.formatted(bindingsTable),
                    binding.querySql(),
                    binding.refreshMode(),
                    binding.refreshIntervalMs(),
                    binding.valueField(),
                    binding.triggerObjectPath(),
                    binding.triggerFunctionName(),
                    binding.enabled(),
                    Timestamp.from(Instant.now()),
                    binding.appId(),
                    binding.objectPath(),
                    binding.variableName()
            );
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO %s (
                    id, app_id, object_path, variable_name, query_sql, refresh_mode,
                    refresh_interval_ms, value_field, trigger_object_path, trigger_function_name,
                    enabled, deployed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(bindingsTable),
                binding.id(),
                binding.appId(),
                binding.objectPath(),
                binding.variableName(),
                binding.querySql(),
                binding.refreshMode(),
                binding.refreshIntervalMs(),
                binding.valueField(),
                binding.triggerObjectPath(),
                binding.triggerFunctionName(),
                binding.enabled(),
                Timestamp.from(Instant.now())
        );
    }

    public List<SqlBinding> listByApp(String appId) {
        return jdbcTemplate.query("""
                SELECT id, app_id, object_path, variable_name, query_sql, refresh_mode,
                       refresh_interval_ms, value_field, trigger_object_path, trigger_function_name,
                       enabled, last_refreshed_at
                FROM %s
                WHERE app_id = ?
                ORDER BY object_path, variable_name
                """.formatted(bindingsTable),
                this::mapRow,
                appId
        );
    }

    public List<SqlBinding> listEnabledForSchedule() {
        return jdbcTemplate.query("""
                SELECT id, app_id, object_path, variable_name, query_sql, refresh_mode,
                       refresh_interval_ms, value_field, trigger_object_path, trigger_function_name,
                       enabled, last_refreshed_at
                FROM %s
                WHERE enabled = ? AND refresh_mode = 'on_schedule'
                """.formatted(bindingsTable),
                this::mapRow,
                true
        );
    }

    public List<SqlBinding> listForEvent(String objectPath, String eventName) {
        return jdbcTemplate.query("""
                SELECT id, app_id, object_path, variable_name, query_sql, refresh_mode,
                       refresh_interval_ms, value_field, trigger_object_path, trigger_function_name,
                       enabled, last_refreshed_at
                FROM %s
                WHERE enabled = ?
                  AND refresh_mode = 'on_event'
                  AND object_path = ?
                  AND (trigger_object_path IS NULL OR trigger_object_path = ?)
                  AND (trigger_function_name IS NULL OR trigger_function_name = ?)
                """.formatted(bindingsTable),
                this::mapRow,
                true,
                objectPath,
                objectPath,
                eventName
        );
    }

    public List<SqlBinding> listAllOnEvent() {
        return jdbcTemplate.query("""
                SELECT id, app_id, object_path, variable_name, query_sql, refresh_mode,
                       refresh_interval_ms, value_field, trigger_object_path, trigger_function_name,
                       enabled, last_refreshed_at
                FROM %s
                WHERE enabled = ? AND refresh_mode = 'on_event'
                """.formatted(bindingsTable),
                this::mapRow,
                true
        );
    }

    public List<SqlBinding> listForFunctionSuccess(String appId, String objectPath, String functionName) {
        return jdbcTemplate.query("""
                SELECT id, app_id, object_path, variable_name, query_sql, refresh_mode,
                       refresh_interval_ms, value_field, trigger_object_path, trigger_function_name,
                       enabled, last_refreshed_at
                FROM %s
                WHERE enabled = ?
                  AND refresh_mode = 'on_function_success'
                  AND app_id = ?
                  AND (
                    (trigger_object_path IS NULL AND trigger_function_name IS NULL)
                    OR (trigger_object_path IS NULL AND trigger_function_name = ?)
                    OR (trigger_object_path = ? AND trigger_function_name = ?)
                    OR (trigger_object_path = ? AND trigger_function_name IS NOT NULL
                        AND %s LIKE %s)
                  )
                """.formatted(
                        bindingsTable,
                        relationalDialect.concatExpression("','", "trigger_function_name", "','"),
                        relationalDialect.concatExpression("'%,'", "?", "',%'")
                ),
                this::mapRow,
                true,
                appId,
                functionName,
                objectPath,
                functionName,
                objectPath,
                functionName
        );
    }

    public void markRefreshed(UUID id) {
        jdbcTemplate.update(
                "UPDATE %s SET last_refreshed_at = ? WHERE id = ?".formatted(bindingsTable),
                Timestamp.from(Instant.now()),
                id
        );
    }

    /** Soft-disable a single binding (e.g. orphan target after refresh ObjectNotFoundException). */
    public void setEnabled(UUID id, boolean enabled) {
        jdbcTemplate.update(
                "UPDATE %s SET enabled = ? WHERE id = ?".formatted(bindingsTable),
                enabled,
                id
        );
    }

    /**
     * Disable scheduled/app bindings whose target object path is {@code path} or under it.
     * Used when tree nodes are deleted so refresh ticks do not chase missing targets.
     */
    public int disableForObjectSubtree(String path) {
        if (path == null || path.isBlank()) {
            return 0;
        }
        return jdbcTemplate.update("""
                UPDATE %s
                SET enabled = ?
                WHERE enabled = ?
                  AND (object_path = ? OR object_path LIKE ?)
                """.formatted(bindingsTable),
                false,
                true,
                path,
                path + ".%"
        );
    }

    public Optional<Instant> lastRefreshedAt(UUID id) {
        return jdbcTemplate.query("""
                SELECT last_refreshed_at FROM %s WHERE id = ?
                """.formatted(bindingsTable),
                rs -> rs.next()
                        ? Optional.ofNullable(rs.getTimestamp("last_refreshed_at")).map(Timestamp::toInstant)
                        : Optional.empty(),
                id
        );
    }

    private SqlBinding mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new SqlBinding(
                UUID.fromString(rs.getString("id")),
                rs.getString("app_id"),
                rs.getString("object_path"),
                rs.getString("variable_name"),
                rs.getString("query_sql"),
                rs.getString("refresh_mode"),
                rs.getObject("refresh_interval_ms") != null
                        ? rs.getLong("refresh_interval_ms")
                        : null,
                rs.getString("value_field"),
                rs.getString("trigger_object_path"),
                rs.getString("trigger_function_name"),
                rs.getBoolean("enabled"),
                rs.getTimestamp("last_refreshed_at") != null
                        ? rs.getTimestamp("last_refreshed_at").toInstant()
                        : null
        );
    }

    public record SqlBinding(
            UUID id,
            String appId,
            String objectPath,
            String variableName,
            String querySql,
            String refreshMode,
            Long refreshIntervalMs,
            String valueField,
            String triggerObjectPath,
            String triggerFunctionName,
            boolean enabled,
            Instant lastRefreshedAt
    ) {
    }
}
