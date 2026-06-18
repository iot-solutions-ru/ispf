package com.ispf.server.alert;

import java.time.Instant;

public record AlertRule(
        String id,
        String name,
        String objectPath,
        String watchVariable,
        String conditionExpr,
        String eventName,
        String payloadVariable,
        boolean enabled,
        boolean edgeTrigger,
        Boolean lastConditionMet,
        Instant createdAt,
        Instant updatedAt
) {
}
