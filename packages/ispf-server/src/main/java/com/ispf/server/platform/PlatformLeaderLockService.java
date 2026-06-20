package com.ispf.server.platform;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * JDBC-based leader lock for scheduled jobs across replicas (PostgreSQL/H2).
 */
@Service
public class PlatformLeaderLockService {

    private final JdbcTemplate jdbcTemplate;
    private final String instanceId = UUID.randomUUID().toString();

    public PlatformLeaderLockService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean tryAcquire(String lockName, Duration ttl) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        int updated = jdbcTemplate.update(
                """
                        UPDATE platform_leader_locks
                        SET holder_id = ?, expires_at = ?
                        WHERE lock_name = ? AND expires_at <= ?
                        """,
                instanceId,
                Timestamp.from(expiresAt),
                lockName,
                Timestamp.from(now)
        );
        if (updated > 0) {
            return true;
        }
        try {
            jdbcTemplate.update(
                    """
                            INSERT INTO platform_leader_locks (lock_name, holder_id, expires_at)
                            VALUES (?, ?, ?)
                            """,
                    lockName,
                    instanceId,
                    Timestamp.from(expiresAt)
            );
            return true;
        } catch (Exception ex) {
            Integer count = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(*) FROM platform_leader_locks
                            WHERE lock_name = ? AND holder_id = ? AND expires_at > ?
                            """,
                    Integer.class,
                    lockName,
                    instanceId,
                    Timestamp.from(now)
            );
            return count != null && count > 0;
        }
    }

    public void release(String lockName) {
        jdbcTemplate.update(
                """
                        DELETE FROM platform_leader_locks
                        WHERE lock_name = ? AND holder_id = ?
                        """,
                lockName,
                instanceId
        );
    }
}
