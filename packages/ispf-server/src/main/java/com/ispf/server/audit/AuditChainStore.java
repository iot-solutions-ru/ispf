package com.ispf.server.audit;

import com.ispf.server.persistence.entity.AuditEventEntity;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
public class AuditChainStore {

    private final JdbcTemplate jdbcTemplate;
    private final Object lock = new Object();

    public AuditChainStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS audit_event_chain (
                    event_id VARCHAR(64) PRIMARY KEY,
                    previous_hash VARCHAR(64),
                    chain_hash VARCHAR(64) NOT NULL,
                    occurred_at TIMESTAMP NOT NULL
                )
                """);
    }

    public void append(AuditEventEntity event) {
        synchronized (lock) {
            String previous = jdbcTemplate.query(
                    "SELECT chain_hash FROM audit_event_chain ORDER BY occurred_at DESC, event_id DESC",
                    rs -> rs.next() ? rs.getString(1) : null
            );
            String chainHash = AuditChain.hash(
                    previous,
                    event.getId(),
                    event.getCategory(),
                    event.getAction(),
                    event.getActor(),
                    event.getTargetType(),
                    event.getTargetId(),
                    event.getDetailsJson(),
                    event.getOccurredAt()
            );
            jdbcTemplate.update(
                    "INSERT INTO audit_event_chain (event_id, previous_hash, chain_hash, occurred_at) VALUES (?, ?, ?, ?)",
                    event.getId(),
                    previous,
                    chainHash,
                    Timestamp.from(event.getOccurredAt() == null ? Instant.now() : event.getOccurredAt())
            );
        }
    }

    public boolean verify() {
        List<AuditChain.Link> links = jdbcTemplate.query("""
                SELECT e.id, e.category, e.action, e.actor, e.target_type, e.target_id, e.details_json, e.occurred_at,
                       c.previous_hash, c.chain_hash
                FROM audit_events e
                JOIN audit_event_chain c ON c.event_id = e.id
                ORDER BY e.occurred_at, e.id
                """, (rs, row) -> new AuditChain.Link(
                rs.getString(1),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                rs.getString(5),
                rs.getString(6),
                rs.getString(7),
                rs.getTimestamp(8) == null ? null : rs.getTimestamp(8).toInstant(),
                rs.getString(9),
                rs.getString(10)
        ));
        return AuditChain.intact(links);
    }
}
