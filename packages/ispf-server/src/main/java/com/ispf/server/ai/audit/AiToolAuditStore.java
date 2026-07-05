package com.ispf.server.ai.audit;

import com.ispf.server.application.data.PlatformSqlCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class AiToolAuditStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String auditTable;

    public AiToolAuditStore(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            PlatformSqlCatalog platformSqlCatalog
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.auditTable = platformSqlCatalog.table("ai_tool_audit");
    }

    public long insert(AuditEntry entry) {
        jdbcTemplate.update("""
                INSERT INTO %s (
                    tool_name, app_id, actor, request_hash, status,
                    provider_id, model_id, context_pack_version, errors_json, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(auditTable),
                entry.toolName(),
                entry.appId(),
                entry.actor(),
                entry.requestHash(),
                entry.status(),
                entry.providerId(),
                entry.blueprintId(),
                entry.contextPackVersion(),
                writeErrors(entry.errors()),
                Timestamp.from(entry.createdAt())
        );
        Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM %s".formatted(auditTable), Long.class);
        return id != null ? id : 0L;
    }

    public List<Map<String, Object>> listByAppId(String appId, int limit) {
        int capped = Math.min(Math.max(limit, 1), 5000);
        return jdbcTemplate.query("""
                SELECT id, tool_name, app_id, actor, request_hash, status,
                       provider_id, model_id, context_pack_version, errors_json, created_at
                FROM %s
                WHERE app_id = ?
                ORDER BY created_at ASC, id ASC
                LIMIT ?
                """.formatted(auditTable),
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("toolName", rs.getString("tool_name"));
                    row.put("appId", rs.getString("app_id"));
                    row.put("actor", rs.getString("actor"));
                    row.put("requestHash", rs.getString("request_hash"));
                    row.put("status", rs.getString("status"));
                    row.put("providerId", rs.getString("provider_id"));
                    row.put("blueprintId", rs.getString("model_id"));
                    row.put("contextPackVersion", rs.getString("context_pack_version"));
                    row.put("errors", rs.getString("errors_json"));
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    row.put("createdAt", createdAt != null ? createdAt.toInstant().toString() : null);
                    return row;
                },
                appId,
                capped
        );
    }

    private String writeErrors(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(errors);
        } catch (Exception ex) {
            return "[\"audit serialization failed\"]";
        }
    }

    public record AuditEntry(
            String toolName,
            String appId,
            String actor,
            String requestHash,
            String status,
            String providerId,
            String blueprintId,
            String contextPackVersion,
            List<String> errors,
            Instant createdAt
    ) {
    }
}
