package com.ispf.server.object;

import java.time.Instant;

public record ObjectChangeEvent(
        ObjectChangeType type,
        String path,
        String variableName,
        Instant timestamp,
        Long revision,
        String changedBy,
        boolean telemetry,
        boolean automationEligible,
        Instant observedAt,
        boolean replicaIngress
) {
    public static ObjectChangeEvent of(ObjectChangeType type, String path) {
        return new ObjectChangeEvent(type, path, null, Instant.now(), null, null, false, true, null, false);
    }

    public static ObjectChangeEvent of(ObjectChangeType type, String path, long revision, String changedBy) {
        return new ObjectChangeEvent(type, path, null, Instant.now(), revision, changedBy, false, true, null, false);
    }

    public static ObjectChangeEvent variableUpdated(String path, String variableName) {
        return variableUpdated(path, variableName, false);
    }

    public static ObjectChangeEvent variableUpdated(String path, String variableName, boolean telemetry) {
        return variableUpdated(path, variableName, telemetry, true);
    }

    public static ObjectChangeEvent variableUpdated(
            String path,
            String variableName,
            boolean telemetry,
            boolean automationEligible
    ) {
        return variableUpdated(path, variableName, telemetry, automationEligible, null);
    }

    public static ObjectChangeEvent variableUpdated(
            String path,
            String variableName,
            boolean telemetry,
            boolean automationEligible,
            Instant observedAt
    ) {
        return new ObjectChangeEvent(
                ObjectChangeType.VARIABLE_UPDATED,
                path,
                variableName,
                Instant.now(),
                null,
                null,
                telemetry,
                automationEligible,
                observedAt,
                false
        );
    }

    /** Follower RAM mirror after NATS live-value sync (ADR-0029) — WS only, no automation/historian/NATS. */
    public static ObjectChangeEvent variableUpdatedReplicaIngress(
            String path,
            String variableName,
            Instant observedAt
    ) {
        return new ObjectChangeEvent(
                ObjectChangeType.VARIABLE_UPDATED,
                path,
                variableName,
                Instant.now(),
                null,
                null,
                false,
                false,
                observedAt,
                true
        );
    }

    public static ObjectChangeEvent variableUpdated(String path, String variableName, long revision, String changedBy) {
        return variableUpdated(path, variableName, revision, changedBy, false);
    }

    public static ObjectChangeEvent variableUpdated(
            String path,
            String variableName,
            long revision,
            String changedBy,
            boolean telemetry
    ) {
        return variableUpdated(path, variableName, revision, changedBy, telemetry, true);
    }

    public static ObjectChangeEvent variableUpdated(
            String path,
            String variableName,
            long revision,
            String changedBy,
            boolean telemetry,
            boolean automationEligible
    ) {
        return new ObjectChangeEvent(
                ObjectChangeType.VARIABLE_UPDATED,
                path,
                variableName,
                Instant.now(),
                revision,
                changedBy,
                telemetry,
                automationEligible,
                null,
                false
        );
    }

    public static ObjectChangeEvent eventFired(String path, String eventName) {
        return new ObjectChangeEvent(
                ObjectChangeType.EVENT_FIRED, path, eventName, Instant.now(), null, null, false, true, null, false
        );
    }

    /** Follower RAM/WS refresh after NATS structure sync (ADR-0030) — no NATS re-fan-out. */
    public static ObjectChangeEvent structureReplicaIngress(ObjectChangeType type, String path) {
        return new ObjectChangeEvent(type, path, null, Instant.now(), null, null, false, false, null, true);
    }

    public static ObjectChangeEvent structureReplicaIngress(
            ObjectChangeType type,
            String path,
            String variableName
    ) {
        return new ObjectChangeEvent(type, path, variableName, Instant.now(), null, null, false, false, null, true);
    }
}
