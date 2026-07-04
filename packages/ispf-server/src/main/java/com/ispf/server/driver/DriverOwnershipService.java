package com.ispf.server.driver;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.NatsProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JDBC-based exclusive driver ownership across cluster replicas (ADR-0028).
 */
@Service
public class DriverOwnershipService {

    private final JdbcTemplate jdbcTemplate;
    private final ClusterProperties clusterProperties;
    private final String instanceId;

    public DriverOwnershipService(
            JdbcTemplate jdbcTemplate,
            ClusterProperties clusterProperties,
            NatsProperties natsProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.clusterProperties = clusterProperties;
        this.instanceId = natsProperties.replicaId();
    }

    public boolean isEnabled() {
        return clusterProperties.isDriverOwnershipActive();
    }

    public String instanceId() {
        return instanceId;
    }

    public Duration lockTtl() {
        return Duration.ofSeconds(clusterProperties.driverLockTtlSeconds());
    }

    public boolean tryAcquire(String devicePath) {
        return tryAcquire(devicePath, lockTtl());
    }

    public boolean tryAcquire(String devicePath, Duration ttl) {
        if (!isEnabled()) {
            return true;
        }
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        int updated = jdbcTemplate.update(
                """
                        UPDATE platform_driver_locks
                        SET holder_id = ?, expires_at = ?
                        WHERE device_path = ? AND expires_at <= ?
                        """,
                instanceId,
                Timestamp.from(expiresAt),
                devicePath,
                Timestamp.from(now)
        );
        if (updated > 0) {
            return true;
        }
        try {
            jdbcTemplate.update(
                    """
                            INSERT INTO platform_driver_locks (device_path, holder_id, expires_at)
                            VALUES (?, ?, ?)
                            """,
                    devicePath,
                    instanceId,
                    Timestamp.from(expiresAt)
            );
            return true;
        } catch (Exception ex) {
            return holdsLock(devicePath, now);
        }
    }

    public boolean renew(String devicePath) {
        return renew(devicePath, lockTtl());
    }

    public boolean renew(String devicePath, Duration ttl) {
        if (!isEnabled()) {
            return true;
        }
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        int updated = jdbcTemplate.update(
                """
                        UPDATE platform_driver_locks
                        SET expires_at = ?
                        WHERE device_path = ? AND holder_id = ? AND expires_at > ?
                        """,
                Timestamp.from(expiresAt),
                devicePath,
                instanceId,
                Timestamp.from(now)
        );
        return updated > 0;
    }

    public void release(String devicePath) {
        if (!isEnabled()) {
            return;
        }
        jdbcTemplate.update(
                """
                        DELETE FROM platform_driver_locks
                        WHERE device_path = ? AND holder_id = ?
                        """,
                devicePath,
                instanceId
        );
    }

    public Optional<String> findOwner(String devicePath) {
        if (!isEnabled()) {
            return Optional.of(instanceId);
        }
        List<String> holders = jdbcTemplate.query(
                """
                        SELECT holder_id FROM platform_driver_locks
                        WHERE device_path = ? AND expires_at > ?
                        """,
                (rs, rowNum) -> rs.getString("holder_id"),
                devicePath,
                Timestamp.from(Instant.now())
        );
        return holders.isEmpty() ? Optional.empty() : Optional.of(holders.getFirst());
    }

    public boolean holdsLock(String devicePath) {
        return holdsLock(devicePath, Instant.now());
    }

    public List<String> listHeldDevicePaths() {
        if (!isEnabled()) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                        SELECT device_path FROM platform_driver_locks
                        WHERE holder_id = ? AND expires_at > ?
                        ORDER BY device_path
                        """,
                (rs, rowNum) -> rs.getString("device_path"),
                instanceId,
                Timestamp.from(Instant.now())
        );
    }

    public List<String> findExpiredDevicePaths() {
        if (!isEnabled()) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                        SELECT device_path FROM platform_driver_locks
                        WHERE expires_at <= ?
                        ORDER BY device_path
                        """,
                (rs, rowNum) -> rs.getString("device_path"),
                Timestamp.from(Instant.now())
        );
    }

    public Map<String, Integer> countLocksByHolder() {
        if (!isEnabled()) {
            return Map.of();
        }
        Map<String, Integer> counts = new HashMap<>();
        jdbcTemplate.query(
                """
                        SELECT holder_id, COUNT(*) AS lock_count FROM platform_driver_locks
                        WHERE expires_at > ?
                        GROUP BY holder_id
                        """,
                rs -> {
                    counts.put(rs.getString("holder_id"), rs.getInt("lock_count"));
                },
                Timestamp.from(Instant.now())
        );
        return counts;
    }

    public int countHeldLocks() {
        if (!isEnabled()) {
            return 0;
        }
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM platform_driver_locks
                        WHERE holder_id = ? AND expires_at > ?
                        """,
                Integer.class,
                instanceId,
                Timestamp.from(Instant.now())
        );
        return count != null ? count : 0;
    }

    private boolean holdsLock(String devicePath, Instant now) {
        if (!isEnabled()) {
            return true;
        }
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM platform_driver_locks
                        WHERE device_path = ? AND holder_id = ? AND expires_at > ?
                        """,
                Integer.class,
                devicePath,
                instanceId,
                Timestamp.from(now)
        );
        return count != null && count > 0;
    }
}
