package com.ispf.server.correlator;

import java.time.Instant;

public record EventCorrelator(
        String id,
        String name,
        String objectPath,
        CorrelatorPatternType patternType,
        String eventName,
        String secondEventName,
        int windowSeconds,
        int minOccurrences,
        int cooldownSeconds,
        int sequenceGapSeconds,
        CorrelatorActionType actionType,
        String actionTarget,
        String payloadFilterExpr,
        boolean enabled,
        Instant lastTriggeredAt,
        String lastActionError,
        Instant createdAt,
        Instant updatedAt
) {
}
