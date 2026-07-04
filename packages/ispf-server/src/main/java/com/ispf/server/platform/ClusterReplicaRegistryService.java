package com.ispf.server.platform;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.NatsProperties;
import com.ispf.server.driver.DriverOwnershipService;
import com.ispf.server.platform.update.PlatformVersionSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class ClusterReplicaRegistryService {

    public enum NodeStatus {
        UP,
        STALE,
        DOWN
    }

    private final JdbcTemplate jdbcTemplate;
    private final ClusterProperties clusterProperties;
    private final NatsProperties natsProperties;
    private final DriverOwnershipService driverOwnershipService;
    private final Optional<BuildProperties> buildProperties;
    private final String environment;

    public ClusterReplicaRegistryService(
            JdbcTemplate jdbcTemplate,
            ClusterProperties clusterProperties,
            NatsProperties natsProperties,
            DriverOwnershipService driverOwnershipService,
            Optional<BuildProperties> buildProperties,
            @Value("${ispf.environment:dev}") String environment
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.clusterProperties = clusterProperties;
        this.natsProperties = natsProperties;
        this.driverOwnershipService = driverOwnershipService;
        this.buildProperties = buildProperties;
        this.environment = environment;
    }

    public void recordHeartbeat() {
        if (!clusterProperties.enabled()) {
            return;
        }
        Instant now = Instant.now();
        String replicaId = natsProperties.replicaId();
        String version = PlatformVersionSupport.currentVersion(buildProperties);
        String javaVersion = Runtime.version().toString();
        int updated = jdbcTemplate.update(
                """
                        UPDATE platform_cluster_replicas
                        SET version = ?, environment = ?, java_version = ?, last_heartbeat_at = ?
                        WHERE replica_id = ?
                        """,
                version,
                environment,
                javaVersion,
                Timestamp.from(now),
                replicaId
        );
        if (updated == 0) {
            jdbcTemplate.update(
                    """
                            INSERT INTO platform_cluster_replicas
                                (replica_id, version, environment, java_version, started_at, last_heartbeat_at)
                            VALUES (?, ?, ?, ?, ?, ?)
                            """,
                    replicaId,
                    version,
                    environment,
                    javaVersion,
                    Timestamp.from(now),
                    Timestamp.from(now)
            );
        }
    }

    public List<ClusterNode> listNodes() {
        String selfId = natsProperties.replicaId();
        if (!clusterProperties.enabled()) {
            return List.of(singleNode(selfId));
        }

        Instant now = Instant.now();
        Map<String, Integer> lockCounts = driverOwnershipService.countLocksByHolder();
        List<ReplicaRow> rows = jdbcTemplate.query(
                """
                        SELECT replica_id, version, environment, java_version, started_at, last_heartbeat_at
                        FROM platform_cluster_replicas
                        ORDER BY replica_id
                        """,
                (rs, rowNum) -> new ReplicaRow(
                        rs.getString("replica_id"),
                        rs.getString("version"),
                        rs.getString("environment"),
                        rs.getString("java_version"),
                        rs.getTimestamp("started_at").toInstant(),
                        rs.getTimestamp("last_heartbeat_at").toInstant()
                )
        );

        Set<String> replicaIds = new HashSet<>();
        for (ReplicaRow row : rows) {
            replicaIds.add(row.replicaId());
        }
        replicaIds.addAll(lockCounts.keySet());
        if (!replicaIds.contains(selfId)) {
            replicaIds.add(selfId);
        }

        List<ClusterNode> nodes = new ArrayList<>();
        for (String replicaId : replicaIds) {
            ReplicaRow row = rows.stream()
                    .filter(candidate -> candidate.replicaId().equals(replicaId))
                    .findFirst()
                    .orElse(null);
            Instant lastHeartbeat = row != null ? row.lastHeartbeatAt() : null;
            int locks = lockCounts.getOrDefault(replicaId, 0);
            NodeStatus status = resolveStatus(lastHeartbeat, locks, now);
            nodes.add(new ClusterNode(
                    replicaId,
                    status.name(),
                    row != null ? row.version() : (replicaId.equals(selfId) ? currentVersion() : null),
                    row != null ? row.environment() : (replicaId.equals(selfId) ? environment : null),
                    row != null ? row.javaVersion() : (replicaId.equals(selfId) ? Runtime.version().toString() : null),
                    row != null ? row.startedAt() : null,
                    lastHeartbeat,
                    locks,
                    replicaId.equals(selfId)
            ));
        }
        nodes.sort(Comparator.comparing(ClusterNode::replicaId));
        return nodes;
    }

    private ClusterNode singleNode(String replicaId) {
        return new ClusterNode(
                replicaId,
                NodeStatus.UP.name(),
                currentVersion(),
                environment,
                Runtime.version().toString(),
                null,
                Instant.now(),
                driverOwnershipService.countHeldLocks(),
                true
        );
    }

    private String currentVersion() {
        return PlatformVersionSupport.currentVersion(buildProperties);
    }

    private NodeStatus resolveStatus(Instant lastHeartbeat, int heldLocks, Instant now) {
        if (lastHeartbeat == null) {
            return heldLocks > 0 ? NodeStatus.STALE : NodeStatus.DOWN;
        }
        long ageSeconds = now.getEpochSecond() - lastHeartbeat.getEpochSecond();
        if (ageSeconds <= clusterProperties.replicaStaleSeconds()) {
            return NodeStatus.UP;
        }
        if (ageSeconds <= clusterProperties.replicaStaleSeconds() * 5L || heldLocks > 0) {
            return NodeStatus.STALE;
        }
        return NodeStatus.DOWN;
    }

    private record ReplicaRow(
            String replicaId,
            String version,
            String environment,
            String javaVersion,
            Instant startedAt,
            Instant lastHeartbeatAt
    ) {
    }

    public record ClusterNode(
            String replicaId,
            String status,
            String version,
            String environment,
            String javaVersion,
            Instant startedAt,
            Instant lastHeartbeatAt,
            int heldDriverLocks,
            boolean self
    ) {
    }
}
