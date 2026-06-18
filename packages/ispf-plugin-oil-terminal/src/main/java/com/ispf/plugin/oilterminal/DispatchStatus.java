package com.ispf.plugin.oilterminal;

import java.util.Set;

/**
 * Dispatch order lifecycle — transitions only via plugin functions.
 */
public enum DispatchStatus {
    PLANNED,
    READY,
    FILLING,
    COMPLETED,
    CLOSED;

    public static DispatchStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return PLANNED;
        }
        return DispatchStatus.valueOf(raw.trim().toUpperCase());
    }

    public String wireValue() {
        return name().toLowerCase();
    }

    public static Set<DispatchStatus> allowedNext(DispatchStatus current) {
        return switch (current) {
            case PLANNED -> Set.of(READY);
            case READY -> Set.of(FILLING);
            case FILLING -> Set.of(COMPLETED);
            case COMPLETED -> Set.of(CLOSED);
            case CLOSED -> Set.of();
        };
    }
}
