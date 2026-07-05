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
        @DefaultValue("500") int liveVariableSyncCoalesceMs,
        /** When false, each live-variable NATS sync is published immediately (no pending map). */
        @DefaultValue("true") boolean liveVariableSyncCoalesceEnabled,
        /** ADR-0032: unified | edge-api | hmi-read | io | compute */
        @DefaultValue("") String replicaProfile,
        /** ADR-0032: comma-separated capability override (replaces profile caps when set). */
        @DefaultValue("") String replicaCapabilities,
        /** Deprecated ADR-0031 alias for replica profile. */
        @DefaultValue("all") String replicaRole,
        /** ADR-0031: consume platform_jobs on this replica. */
        @DefaultValue("true") boolean jobConsumerEnabled,
        @DefaultValue("2000") int jobPollMs,
        @DefaultValue("2") int jobMaxConcurrent,
        @DefaultValue("true") boolean jobElasticEnabled,
        @DefaultValue("1") int jobMaxConcurrentMin,
        @DefaultValue("8") int jobMaxConcurrentMax,
        @DefaultValue("50") int jobElasticScaleUpThreshold,
        @DefaultValue("6") int jobElasticScaleDownSteps,
        @DefaultValue("500") int jobElasticScaleCheckIntervalMs,
        @DefaultValue("1800") int jobRunningTtlSeconds
) {
    public ReplicaCapabilitySet effectiveCapabilities() {
        if (!enabled) {
            return ReplicaCapabilitySet.unified();
        }
        return ReplicaCapabilitySet.resolve(replicaProfile, replicaRole, replicaCapabilities);
    }

    public ReplicaProfile parsedReplicaProfile() {
        return effectiveCapabilities().profile();
    }

    @Deprecated
    public ClusterReplicaRole parsedReplicaRole() {
        return ClusterReplicaRole.parse(
                replicaProfile != null && !replicaProfile.isBlank()
                        ? replicaProfile
                        : replicaRole
        );
    }

    public boolean hasCapability(ReplicaCapability capability) {
        return effectiveCapabilities().has(capability);
    }

    public boolean isDriverOwnershipActive() {
        return enabled && driverOwnershipEnabled && hasCapability(ReplicaCapability.DRIVERS);
    }

    /** ADR-0029: NATS live-value mirror to follower RAM. */
    public boolean isLiveVariableSyncActive() {
        return enabled && liveVariableSyncEnabled && hasCapability(ReplicaCapability.REPLICA_SYNC);
    }

    /** ADR-0029: Redis-backed global WS path interest for demand-driven publish on owner. */
    public boolean isClusterPathInterestActive() {
        return enabled && clusterPathInterestEnabled;
    }

    /** ADR-0031: poll and execute platform_jobs on this JVM. */
    public boolean isJobConsumerActive() {
        if (!jobConsumerEnabled) {
            return false;
        }
        if (!enabled) {
            return true;
        }
        return hasCapability(ReplicaCapability.JOBS);
    }

    public int resolvedJobMaxConcurrentMin() {
        return jobElasticEnabled ? jobMaxConcurrentMin : jobMaxConcurrent;
    }

    public int resolvedJobMaxConcurrentMax() {
        return jobElasticEnabled ? jobMaxConcurrentMax : jobMaxConcurrent;
    }

    public boolean isSchedulerActive() {
        if (!enabled) {
            return true;
        }
        return hasCapability(ReplicaCapability.SCHEDULERS);
    }

    public boolean isHttpPublicActive() {
        if (!enabled) {
            return true;
        }
        return hasCapability(ReplicaCapability.HTTP_PUBLIC);
    }

    public boolean isWsActive() {
        if (!enabled) {
            return true;
        }
        return hasCapability(ReplicaCapability.WS);
    }

    public boolean isConfigWriteActive() {
        if (!enabled) {
            return true;
        }
        return hasCapability(ReplicaCapability.CONFIG_WRITE);
    }
}
