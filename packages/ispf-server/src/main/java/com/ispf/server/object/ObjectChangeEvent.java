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
        boolean automationEligible
) {
    public static ObjectChangeEvent of(ObjectChangeType type, String path) {
        return new ObjectChangeEvent(type, path, null, Instant.now(), null, null, false, true);
    }

    public static ObjectChangeEvent of(ObjectChangeType type, String path, long revision, String changedBy) {
        return new ObjectChangeEvent(type, path, null, Instant.now(), revision, changedBy, false, true);
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
        return new ObjectChangeEvent(
                ObjectChangeType.VARIABLE_UPDATED,
                path,
                variableName,
                Instant.now(),
                null,
                null,
                telemetry,
                automationEligible
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
                automationEligible
        );
    }

    public static ObjectChangeEvent eventFired(String path, String eventName) {
        return new ObjectChangeEvent(ObjectChangeType.EVENT_FIRED, path, eventName, Instant.now(), null, null, false, true);
    }
}
