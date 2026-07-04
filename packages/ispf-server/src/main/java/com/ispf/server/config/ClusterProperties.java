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
        @DefaultValue("30") int replicaStaleSeconds
) {
    public boolean isDriverOwnershipActive() {
        return enabled && driverOwnershipEnabled;
    }
}
