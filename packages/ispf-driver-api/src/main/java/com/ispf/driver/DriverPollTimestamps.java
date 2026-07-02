package com.ispf.driver;

import java.time.Instant;

/**
 * Observation timestamps for driver poll ticks (BL-79, ADR-0020).
 */
public final class DriverPollTimestamps {

    private DriverPollTimestamps() {
    }

    /** Single instant shared by all points updated in one {@code readPoints} tick. */
    public static Instant pollTick() {
        return Instant.now();
    }

    public static Instant sourceOrPollTick(Instant sourceTimestamp) {
        return sourceTimestamp != null ? sourceTimestamp : pollTick();
    }
}
