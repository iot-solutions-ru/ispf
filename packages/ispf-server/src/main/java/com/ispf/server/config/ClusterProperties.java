package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "ispf.cluster")
public record ClusterProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("true") boolean driverOwnershipEnabled,
        @DefaultValue("30") int driverLockTtlSeconds,
        @DefaultValue("10") int driverLockRenewSeconds,
        @DefaultValue("15") int driverFailoverScanSeconds,
        @DefaultValue("10") int replicaHeartbeatSeconds,
        @DefaultValue("30") int replicaStaleSeconds,
        @DefaultValue("true") boolean liveVariableSyncEnabled,
        @DefaultValue("true") boolean clusterPathInterestEnabled,
        /** ADR-0029: NATS fan-out coalesce window (independent of runtime-telemetry.coalesce-ms). */
        @DefaultValue("500") int liveVariableSyncCoalesceMs
) {
    public boolean isDriverOwnershipActive() {
        return enabled && driverOwnershipEnabled;
    }

    /** ADR-0029: NATS live-value mirror to follower RAM. */
    public boolean isLiveVariableSyncActive() {
        return enabled && liveVariableSyncEnabled;
    }

    /** ADR-0029: Redis-backed global WS path interest for demand-driven publish on owner. */
    public boolean isClusterPathInterestActive() {
        return enabled && clusterPathInterestEnabled;
    }
}
