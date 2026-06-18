package com.ispf.server.correlator;

import java.time.Instant;

public record EventCorrelator(
        String id,
        String name,
        String objectPath,
        String eventName,
        int windowSeconds,
        int minOccurrences,
        int cooldownSeconds,
        CorrelatorActionType actionType,
        String actionTarget,
        boolean enabled,
        Instant lastTriggeredAt,
        Instant createdAt,
        Instant updatedAt
) {
}
