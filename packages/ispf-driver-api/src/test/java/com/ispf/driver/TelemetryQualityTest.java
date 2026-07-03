package com.ispf.driver;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryQualityTest {

    @Test
    void parsesNormalizedLevels() {
        assertEquals(TelemetryQuality.Level.GOOD, TelemetryQuality.parse("GOOD"));
        assertEquals(TelemetryQuality.Level.UNCERTAIN, TelemetryQuality.parse("uncertain"));
        assertEquals(TelemetryQuality.Level.BAD, TelemetryQuality.parse("bad"));
    }

    @Test
    void badIsNotPlottable() {
        assertFalse(TelemetryQuality.isPlottable("BAD"));
        assertTrue(TelemetryQuality.isPlottable("GOOD"));
        assertTrue(TelemetryQuality.isPlottable("UNCERTAIN"));
    }
}
