package com.ispf.server.object;

import com.ispf.server.relational.RelationalDialect;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ObjectConfigAuditService {

    private final JdbcTemplate jdbcTemplate;
    private final RelationalDialect relationalDialect;

    public ObjectConfigAuditService(JdbcTemplate jdbcTemplate, RelationalDialect relationalDialect) {
        this.jdbcTemplate = jdbcTemplate;
        this.relationalDialect = relationalDialect;
    }

    public void record(
            String objectPath,
            String changeType,
            String field,
            String actor,
            long revisionBefore,
            long revisionAfter,
            String summaryJson
    ) {
        jdbcTemplate.update("""
                INSERT INTO object_config_audit (
                    id, object_path, change_type, field, actor,
                    occurred_at, revision_before, revision_after, summary_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID().toString(),
                objectPath,
                changeType,
                field,
                actor,
                Timestamp.from(Instant.now()),
                revisionBefore,
                revisionAfter,
                summaryJson
        );
    }

    public List<AuditEntry> list(String objectPath, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Audit limit must be positive");
        }
        int capped = Math.min(limit, 200);
        return jdbcTemplate.query("""
                SELECT id, object_path, change_type, field, actor,
                       occurred_at, revision_before, revision_after, summary_json
                FROM object_config_audit
                WHERE object_path = ?
                ORDER BY occurred_at DESC, id DESC
                """ + relationalDialect.limitClause(),
                (rs, rowNum) -> new AuditEntry(
                        rs.getString("id"),
                        rs.getString("object_path"),
                        rs.getString("change_type"),
                        rs.getString("field"),
                        rs.getString("actor"),
                        rs.getTimestamp("occurred_at").toInstant(),
                        rs.getLong("revision_before"),
                        rs.getLong("revision_after"),
                        rs.getString("summary_json")
                ),
                objectPath,
                capped
        );
    }

    public record AuditEntry(
            String id,
            String objectPath,
            String changeType,
            String field,
            String actor,
            Instant occurredAt,
            long revisionBefore,
            long revisionAfter,
            String summaryJson
    ) {
    }
}
