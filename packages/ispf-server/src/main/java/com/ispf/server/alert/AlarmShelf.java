package com.ispf.server.alert;

import java.time.Instant;

public record AlarmShelf(
        String id,
        String objectPath,
        String eventName,
        String alertRulePath,
        String shelvedBy,
        Instant shelvedAt,
        Instant expiresAt,
        String comment,
        boolean active
) {
}
