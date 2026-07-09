package com.ispf.server.config;

import java.util.Locale;

/**
 * ADR-0032: composable cluster replica capabilities.
 */
public enum ReplicaCapability {
    HTTP_PUBLIC("http-public"),
    WS("ws"),
    CONFIG_WRITE("config-write"),
    DRIVERS("drivers"),
    REPLICA_SYNC("replica-sync"),
    JOBS("jobs"),
    SCHEDULERS("schedulers"),
    /** BL-207: analytics engine materializer, rollup backfill, heavy historian workloads */
    ANALYTICS("analytics");

    private final String externalName;

    ReplicaCapability(String externalName) {
        this.externalName = externalName;
    }

    public String externalName() {
        return externalName;
    }

    public static ReplicaCapability parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Replica capability must not be blank");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (ReplicaCapability capability : values()) {
            if (capability.externalName.equals(normalized) || capability.name().equalsIgnoreCase(normalized)) {
                return capability;
            }
        }
        throw new IllegalArgumentException("Unknown replica capability: " + raw);
    }
}
