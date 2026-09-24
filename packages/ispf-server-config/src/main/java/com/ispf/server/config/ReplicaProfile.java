package com.ispf.server.config;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * ADR-0032: named presets expanding to {@link ReplicaCapability} sets.
 */
public enum ReplicaProfile {
    UNIFIED("unified", EnumSet.allOf(ReplicaCapability.class)),
    EDGE_API("edge-api", EnumSet.of(
            ReplicaCapability.HTTP_PUBLIC,
            ReplicaCapability.WS,
            ReplicaCapability.REPLICA_SYNC,
            ReplicaCapability.CONFIG_WRITE,
            ReplicaCapability.SCHEDULERS
    )),
    HMI_READ("hmi-read", EnumSet.of(
            ReplicaCapability.HTTP_PUBLIC,
            ReplicaCapability.WS,
            ReplicaCapability.REPLICA_SYNC
    )),
    IO("io", EnumSet.of(
            ReplicaCapability.DRIVERS,
            ReplicaCapability.REPLICA_SYNC,
            ReplicaCapability.SCHEDULERS
    )),
    COMPUTE("compute", EnumSet.of(
            ReplicaCapability.JOBS,
            ReplicaCapability.REPLICA_SYNC
    )),
    ANALYTICS("analytics", EnumSet.of(
            ReplicaCapability.ANALYTICS,
            ReplicaCapability.REPLICA_SYNC,
            ReplicaCapability.SCHEDULERS
    ));

    private final String externalName;
    private final Set<ReplicaCapability> capabilities;

    ReplicaProfile(String externalName, Set<ReplicaCapability> capabilities) {
        this.externalName = externalName;
        this.capabilities = EnumSet.copyOf(capabilities);
    }

    public String externalName() {
        return externalName;
    }

    public Set<ReplicaCapability> capabilities() {
        return EnumSet.copyOf(capabilities);
    }

    /** Deprecated ADR-0031 alias for UI backward compatibility. */
    public String legacyRoleName() {
        return switch (this) {
            case UNIFIED -> "all";
            case EDGE_API -> "api";
            case COMPUTE -> "worker";
            case HMI_READ -> "hmi-read";
            case IO -> "io";
            case ANALYTICS -> "analytics";
        };
    }

    public static ReplicaProfile parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNIFIED;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "unified", "all" -> UNIFIED;
            case "edge-api", "api" -> EDGE_API;
            case "hmi-read" -> HMI_READ;
            case "io", "driver", "drivers" -> IO;
            case "compute", "worker" -> COMPUTE;
            case "analytics" -> ANALYTICS;
            default -> throw new IllegalArgumentException(
                    "Unknown replica profile: " + raw
                            + " (expected unified, edge-api, hmi-read, io, compute, analytics)");
        };
    }
}
