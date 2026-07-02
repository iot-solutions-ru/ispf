package com.ispf.server.object.pubsub;

/**
 * Subscribers for {@code EVENT_FIRED} at {@code (objectPath, eventName)} (ADR-0024).
 */
public record EventFiredInterest(
        boolean bindings,
        boolean correlators,
        boolean workflows,
        boolean sqlBindings
) {
    public static final EventFiredInterest NONE = new EventFiredInterest(false, false, false, false);

    public boolean hasAny() {
        return bindings || correlators || workflows || sqlBindings;
    }
}
