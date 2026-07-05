package com.ispf.server.platform;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.ReplicaCapabilitySet;
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
    private final int serverPort;

    public ClusterReplicaRegistryService(
            JdbcTemplate jdbcTemplate,
            ClusterProperties clusterProperties,
            NatsProperties natsProperties,
            DriverOwnershipService driverOwnershipService,
            Optional<BuildProperties> buildProperties,
            @Value("${ispf.environment:dev}") String environment,
            @Value("${server.port:8080}") int serverPort
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.clusterProperties = clusterProperties;
        this.natsProperties = natsProperties;
        this.driverOwnershipService = driverOwnershipService;
        this.buildProperties = buildProperties;
        this.environment = environment;
        this.serverPort = serverPort;
    }

    public void recordHeartbeat() {
        if (!clusterProperties.enabled()) {
            return;
        }
        Instant now = Instant.now();
        String replicaId = natsProperties.replicaId();
        String version = PlatformVersionSupport.currentVersion(buildProperties);
        String javaVersion = Runtime.version().toString();
        ReplicaCapabilitySet capabilitySet = clusterProperties.effectiveCapabilities();
        String profile = capabilitySet.profile().externalName();
        String capabilities = capabilitySet.serialized();
        String legacyRole = capabilitySet.profile().legacyRoleName();
        int updated = jdbcTemplate.update(
                """
                        UPDATE platform_cluster_replicas
                        SET version = ?, environment = ?, java_version = ?,
                            replica_role = ?, replica_profile = ?, replica_capabilities = ?,
                            http_port = ?, last_heartbeat_at = ?
                        WHERE replica_id = ?
                        """,
                version,
                environment,
                javaVersion,
                legacyRole,
                profile,
                capabilities,
                serverPort,
                Timestamp.from(now),
                replicaId
        );
        if (updated == 0) {
            jdbcTemplate.update(
                    """
                            INSERT INTO platform_cluster_replicas
                                (replica_id, version, environment, java_version,
                                 replica_role, replica_profile, replica_capabilities,
                                 http_port, started_at, last_heartbeat_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    replicaId,
                    version,
                    environment,
                    javaVersion,
                    legacyRole,
                    profile,
                    capabilities,
                    serverPort,
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
        Map<String, Integer> lockCounts = driverOwnershipService.countLocksByHolderForClusterView();
        List<ReplicaRow> rows = jdbcTemplate.query(
                """
                        SELECT replica_id, version, environment, java_version,
                               replica_role, replica_profile, replica_capabilities,
                               http_port, started_at, last_heartbeat_at
                        FROM platform_cluster_replicas
                        ORDER BY replica_id
                        """,
                (rs, rowNum) -> new ReplicaRow(
                        rs.getString("replica_id"),
                        rs.getString("version"),
                        rs.getString("environment"),
                        rs.getString("java_version"),
                        rs.getString("replica_role"),
                        rs.getString("replica_profile"),
                        rs.getString("replica_capabilities"),
                        rs.getObject("http_port") != null ? rs.getInt("http_port") : null,
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
                    row != null ? row.replicaRole() : selfLegacyRole(selfId, replicaId),
                    row != null ? row.replicaProfile() : selfProfile(selfId, replicaId),
                    row != null ? row.replicaCapabilities() : selfCapabilities(selfId, replicaId),
                    row != null ? row.httpPort() : (replicaId.equals(selfId) ? serverPort : null),
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
        ReplicaCapabilitySet capabilitySet = clusterProperties.effectiveCapabilities();
        return new ClusterNode(
                replicaId,
                NodeStatus.UP.name(),
                currentVersion(),
                environment,
                Runtime.version().toString(),
                capabilitySet.profile().legacyRoleName(),
                capabilitySet.profile().externalName(),
                capabilitySet.serialized(),
                serverPort,
                null,
                Instant.now(),
                driverOwnershipService.countHeldLocks(),
                true
        );
    }

    private String selfLegacyRole(String selfId, String replicaId) {
        return replicaId.equals(selfId)
                ? clusterProperties.effectiveCapabilities().profile().legacyRoleName()
                : null;
    }

    private String selfProfile(String selfId, String replicaId) {
        return replicaId.equals(selfId)
                ? clusterProperties.effectiveCapabilities().profile().externalName()
                : null;
    }

    private String selfCapabilities(String selfId, String replicaId) {
        return replicaId.equals(selfId)
                ? clusterProperties.effectiveCapabilities().serialized()
                : null;
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
            String replicaRole,
            String replicaProfile,
            String replicaCapabilities,
            Integer httpPort,
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
            String replicaRole,
            String replicaProfile,
            String replicaCapabilities,
            Integer httpPort,
            Instant startedAt,
            Instant lastHeartbeatAt,
            int heldDriverLocks,
            boolean self
    ) {
    }
}
