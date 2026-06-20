package com.ispf.driver.gpstracker;

/**
 * Point mapping: {@code feed} returns the last received line/buffer from connected devices.
 */
public record GpsTrackerPoint(GpsTrackerMode mode) {

    public enum GpsTrackerMode {
        FEED
    }

    public static GpsTrackerPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("GPS tracker point mapping is blank");
        }
        if ("feed".equalsIgnoreCase(raw.trim())) {
            return new GpsTrackerPoint(GpsTrackerMode.FEED);
        }
        throw new IllegalArgumentException("Unknown GPS tracker point mapping: " + raw);
    }
}
