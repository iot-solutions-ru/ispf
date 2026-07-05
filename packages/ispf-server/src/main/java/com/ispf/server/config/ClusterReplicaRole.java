package com.ispf.server.config;

import java.util.Locale;

/**
 * Deprecated ADR-0031 role enum. Use {@link ReplicaProfile} / {@link ReplicaCapabilitySet} (ADR-0032).
 */
@Deprecated
public enum ClusterReplicaRole {
    ALL,
    API,
    WORKER;

    public boolean allowsDrivers() {
        return this == ALL;
    }

    public boolean allowsJobConsumption() {
        return this == ALL || this == WORKER;
    }

    public boolean allowsApi() {
        return this == ALL || this == API;
    }

    public String externalName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ClusterReplicaRole parse(String raw) {
        return switch (ReplicaProfile.parse(raw)) {
            case UNIFIED -> ALL;
            case EDGE_API, HMI_READ -> API;
            case COMPUTE -> WORKER;
            case IO -> ALL;
        };
    }
}
