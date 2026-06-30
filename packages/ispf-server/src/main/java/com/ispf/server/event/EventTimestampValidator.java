package com.ispf.server.event;

import com.ispf.server.config.EventJournalProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class EventTimestampValidator {

    private static final Duration DEFAULT_MAX_FUTURE_SKEW = Duration.ofMinutes(5);

    private final EventJournalProperties eventJournalProperties;

    public EventTimestampValidator(EventJournalProperties eventJournalProperties) {
        this.eventJournalProperties = eventJournalProperties;
    }

    public Instant validateOccurredAt(Instant occurredAt) {
        if (occurredAt == null) {
            return Instant.now();
        }
        Instant now = Instant.now();
        if (occurredAt.isAfter(now.plus(DEFAULT_MAX_FUTURE_SKEW))) {
            throw new IllegalArgumentException("occurredAt is too far in the future");
        }
        int retentionDays = Math.max(1, eventJournalProperties.getRetentionDays());
        Instant oldest = now.minus(Duration.ofDays(retentionDays));
        if (occurredAt.isBefore(oldest)) {
            throw new IllegalArgumentException("occurredAt is older than event retention window");
        }
        return occurredAt;
    }
}
