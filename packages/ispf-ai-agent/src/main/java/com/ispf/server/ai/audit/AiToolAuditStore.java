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
                    provider_id, model_id, context_pack_version, errors_json, created_at,
                    latency_ms, prompt_tokens, completion_tokens, turn_id, step_no,
                    interaction_mode, prompt_profile
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                Timestamp.from(entry.createdAt()),
                entry.latencyMs(),
                entry.promptTokens(),
                entry.completionTokens(),
                entry.turnId(),
                entry.stepNo(),
                entry.interactionMode(),
                entry.promptProfile()
        );
        Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM %s".formatted(auditTable), Long.class);
        return id != null ? id : 0L;
    }

    public List<Map<String, Object>> listByAppId(String appId, int limit) {
        int capped = Math.min(Math.max(limit, 1), 5000);
        return jdbcTemplate.query("""
                SELECT id, tool_name, app_id, actor, request_hash, status,
                       provider_id, model_id, context_pack_version, errors_json, created_at,
                       latency_ms, prompt_tokens, completion_tokens, turn_id, step_no,
                       interaction_mode, prompt_profile
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
                    long latency = rs.getLong("latency_ms");
                    if (!rs.wasNull()) {
                        row.put("latencyMs", latency);
                    }
                    int promptTokens = rs.getInt("prompt_tokens");
                    if (!rs.wasNull()) {
                        row.put("promptTokens", promptTokens);
                    }
                    int completionTokens = rs.getInt("completion_tokens");
                    if (!rs.wasNull()) {
                        row.put("completionTokens", completionTokens);
                    }
                    row.put("turnId", rs.getString("turn_id"));
                    int stepNo = rs.getInt("step_no");
                    if (!rs.wasNull()) {
                        row.put("stepNo", stepNo);
                    }
                    row.put("interactionMode", rs.getString("interaction_mode"));
                    row.put("promptProfile", rs.getString("prompt_profile"));
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
            Instant createdAt,
            Long latencyMs,
            Integer promptTokens,
            Integer completionTokens,
            String turnId,
            Integer stepNo,
            String interactionMode,
            String promptProfile
    ) {
        public AuditEntry(
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
            this(toolName, appId, actor, requestHash, status, providerId, blueprintId,
                    contextPackVersion, errors, createdAt, null, null, null, null, null, null, null);
        }
    }
}
