package com.ispf.server.application.catalog;

import com.ispf.server.application.data.PlatformSqlCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ApplicationEventCatalogStore {

    private final JdbcTemplate jdbcTemplate;
    private final String catalogTable;

    public ApplicationEventCatalogStore(JdbcTemplate jdbcTemplate, PlatformSqlCatalog platformSqlCatalog) {
        this.jdbcTemplate = jdbcTemplate;
        this.catalogTable = platformSqlCatalog.table("application_event_catalog");
    }

    public void replaceForApp(String appId, List<EventCatalogEntry> entries) {
        jdbcTemplate.update("DELETE FROM %s WHERE app_id = ?".formatted(catalogTable), appId);
        Instant now = Instant.now();
        for (EventCatalogEntry entry : entries) {
            jdbcTemplate.update("""
                    INSERT INTO %s (app_id, event_id, roles_json, payload_schema_json, updated_at)
                    VALUES (?, ?, ?, ?, ?)
                    """.formatted(catalogTable),
                    appId,
                    entry.eventId(),
                    entry.rolesJson(),
                    entry.payloadSchemaJson(),
                    Timestamp.from(now)
            );
        }
    }

    public List<Map<String, Object>> listForApp(String appId) {
        return jdbcTemplate.query("""
                SELECT event_id, roles_json, payload_schema_json, updated_at
                FROM %s
                WHERE app_id = ?
                ORDER BY event_id
                """.formatted(catalogTable),
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("event_id"));
                    row.put("rolesJson", rs.getString("roles_json"));
                    row.put("payloadSchemaJson", rs.getString("payload_schema_json"));
                    row.put("updatedAt", rs.getTimestamp("updated_at"));
                    return row;
                },
                appId
        );
    }

    public Optional<EventCatalogEntry> find(String appId, String eventId) {
        List<EventCatalogEntry> rows = jdbcTemplate.query("""
                SELECT event_id, roles_json, payload_schema_json
                FROM %s
                WHERE app_id = ? AND event_id = ?
                """.formatted(catalogTable),
                (rs, rowNum) -> new EventCatalogEntry(
                        rs.getString("event_id"),
                        rs.getString("roles_json"),
                        rs.getString("payload_schema_json")
                ),
                appId,
                eventId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public record EventCatalogEntry(String eventId, String rolesJson, String payloadSchemaJson) {
    }
}
