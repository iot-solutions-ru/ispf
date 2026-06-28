package com.ispf.server.operator;

import com.ispf.server.application.data.PlatformSqlCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class OperatorAgentMemoryStore {

    private final JdbcTemplate jdbcTemplate;
    private final String table;

    public OperatorAgentMemoryStore(JdbcTemplate jdbcTemplate, PlatformSqlCatalog platformSqlCatalog) {
        this.jdbcTemplate = jdbcTemplate;
        this.table = platformSqlCatalog.table("operator_agent_memory");
    }

    public void upsert(OperatorAgentMemoryRecord record) {
        Optional<OperatorAgentMemoryRecord> existing = findByTopic(record.appId(), record.topic());
        if (existing.isPresent()) {
            jdbcTemplate.update("""
                    UPDATE %s
                    SET kind = ?, content = ?, source_actor = ?, source_turn_id = ?, updated_at = ?
                    WHERE memory_id = ?
                    """.formatted(table),
                    record.kind(),
                    record.content(),
                    record.sourceActor(),
                    record.sourceTurnId(),
                    Timestamp.from(record.updatedAt()),
                    existing.get().memoryId()
            );
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO %s (
                    memory_id, app_id, kind, topic, content, source_actor, source_turn_id,
                    use_count, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(table),
                record.memoryId(),
                record.appId(),
                record.kind(),
                record.topic(),
                record.content(),
                record.sourceActor(),
                record.sourceTurnId(),
                record.useCount(),
                Timestamp.from(record.createdAt()),
                Timestamp.from(record.updatedAt())
        );
    }

    public List<OperatorAgentMemoryRecord> listForApp(String appId, int limit) {
        int capped = Math.min(Math.max(limit, 1), 200);
        return jdbcTemplate.query("""
                SELECT memory_id, app_id, kind, topic, content, source_actor, source_turn_id,
                       use_count, created_at, updated_at
                FROM %s
                WHERE app_id = ?
                ORDER BY updated_at DESC
                LIMIT ?
                """.formatted(table),
                (rs, rowNum) -> mapRow(rs),
                appId,
                capped
        );
    }

    public List<OperatorAgentMemoryRecord> search(String appId, String query, int limit) {
        int capped = Math.min(Math.max(limit, 1), 50);
        String pattern = "%" + query.trim().toLowerCase() + "%";
        return jdbcTemplate.query("""
                SELECT memory_id, app_id, kind, topic, content, source_actor, source_turn_id,
                       use_count, created_at, updated_at
                FROM %s
                WHERE app_id = ?
                  AND (
                    LOWER(topic) LIKE ?
                    OR LOWER(content) LIKE ?
                  )
                ORDER BY use_count DESC, updated_at DESC
                LIMIT ?
                """.formatted(table),
                (rs, rowNum) -> mapRow(rs),
                appId,
                pattern,
                pattern,
                capped
        );
    }

    public int countForApp(String appId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM %s WHERE app_id = ?".formatted(table),
                Integer.class,
                appId
        );
        return count != null ? count : 0;
    }

    public void incrementUseCount(List<String> memoryIds) {
        if (memoryIds == null || memoryIds.isEmpty()) {
            return;
        }
        for (String memoryId : memoryIds) {
            jdbcTemplate.update(
                    "UPDATE %s SET use_count = use_count + 1, updated_at = ? WHERE memory_id = ?".formatted(table),
                    Timestamp.from(Instant.now()),
                    memoryId
            );
        }
    }

    public Optional<OperatorAgentMemoryRecord> findByTopic(String appId, String topic) {
        List<OperatorAgentMemoryRecord> rows = jdbcTemplate.query("""
                SELECT memory_id, app_id, kind, topic, content, source_actor, source_turn_id,
                       use_count, created_at, updated_at
                FROM %s WHERE app_id = ? AND topic = ?
                """.formatted(table),
                (rs, rowNum) -> mapRow(rs),
                appId,
                topic
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public static String newMemoryId() {
        return UUID.randomUUID().toString();
    }

    private static OperatorAgentMemoryRecord mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OperatorAgentMemoryRecord(
                rs.getString("memory_id"),
                rs.getString("app_id"),
                rs.getString("kind"),
                rs.getString("topic"),
                rs.getString("content"),
                rs.getString("source_actor"),
                rs.getString("source_turn_id"),
                rs.getInt("use_count"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}
