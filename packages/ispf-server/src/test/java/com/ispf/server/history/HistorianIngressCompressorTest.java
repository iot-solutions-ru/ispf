package com.ispf.server.history;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistorianIngressCompressorTest {

    @Test
    void zeroLimitsKeepEverySample() {
        HistorianIngressCompressor compressor = new HistorianIngressCompressor(0, 0);
        Instant t0 = Instant.parse("2026-11-12T10:00:00Z");
        assertTrue(compressor.accept("a", t0, 1.0));
        assertTrue(compressor.accept("a", t0.plusSeconds(1), 1.0));
    }

    @Test
    void exceptionDeadbandDropsSmallMoves() {
        HistorianIngressCompressor compressor = new HistorianIngressCompressor(0.5, 0);
        Instant t0 = Instant.parse("2026-11-12T10:00:00Z");
        assertTrue(compressor.accept("tank", t0, 10.0));
        assertFalse(compressor.accept("tank", t0.plusSeconds(1), 10.2));
        assertTrue(compressor.accept("tank", t0.plusSeconds(2), 11.0));
    }

    @Test
    void swingingDoorKeepsABreakInSlope() {
        HistorianIngressCompressor compressor = new HistorianIngressCompressor(0, 0.1);
        Instant t0 = Instant.parse("2026-11-12T10:00:00Z");
        assertTrue(compressor.accept("line", t0, 0.0));
        assertFalse(compressor.accept("line", t0.plusSeconds(10), 0.5));
        assertTrue(compressor.accept("line", t0.plusSeconds(20), 5.0));
    }
}
