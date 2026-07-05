package com.ispf.driver.ingress;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriverIngressTest {

    @Test
    void resolveCoalesceEnabledDefaultsToTrue() {
        assertTrue(DriverIngress.resolveCoalesceEnabled(Map.of(), true));
    }

    @Test
    void resolveCoalesceEnabledParsesFalse() {
        assertFalse(DriverIngress.resolveCoalesceEnabled(
                Map.of(DriverIngress.INGRESS_COALESCE_ENABLED, "false"),
                true
        ));
    }

    @Test
    void resolveFifoIngressForEventJournalOnly() {
        assertTrue(DriverIngress.resolveFifoIngress(
                Map.of(DriverIngress.TELEMETRY_PUBLISH_MODE, "EVENT_JOURNAL_ONLY"),
                true
        ));
    }

    @Test
    void resolveFifoIngressWhenCoalesceDisabled() {
        assertTrue(DriverIngress.resolveFifoIngress(
                Map.of(DriverIngress.INGRESS_COALESCE_ENABLED, "false"),
                true
        ));
    }

    @Test
    void resolveCoalesceBufferWhenCoalesceEnabled() {
        assertFalse(DriverIngress.resolveFifoIngress(Map.of(), true));
    }
}
