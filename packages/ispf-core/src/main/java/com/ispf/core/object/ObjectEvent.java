package com.ispf.core.object;

import com.ispf.core.model.DataRecord;

import java.time.Instant;
import java.util.UUID;

/**
 * An emitted event instance from an object.
 */
public record ObjectEvent(
        String id,
        String objectPath,
        String eventName,
        EventLevel level,
        DataRecord payload,
        Instant timestamp
) {
    public static ObjectEvent of(String objectPath, String eventName, EventLevel level, DataRecord payload) {
        return new ObjectEvent(
                UUID.randomUUID().toString(),
                objectPath,
                eventName,
                level,
                payload,
                Instant.now()
        );
    }
}
