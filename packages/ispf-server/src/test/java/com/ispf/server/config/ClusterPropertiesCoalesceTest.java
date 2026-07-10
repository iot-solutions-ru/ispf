package com.ispf.server.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClusterPropertiesCoalesceTest {

    @Test
    void coalesceInactiveWhenMsZero() {
        assertFalse(cluster(0, true).isLiveVariableSyncCoalesceActive());
    }

    @Test
    void coalesceInactiveWhenDisabled() {
        assertFalse(cluster(500, false).isLiveVariableSyncCoalesceActive());
    }

    @Test
    void coalesceActiveWhenMsPositiveAndEnabled() {
        assertTrue(cluster(500, true).isLiveVariableSyncCoalesceActive());
    }

    private static ClusterProperties cluster(int coalesceMs, boolean coalesceEnabled) {
        return new ClusterProperties(
                true,
                true,
                30,
                10,
                15,
                10,
                30,
                true,
                2,
                8,
                4,
                6,
                500,
                true,
                true,
                coalesceMs,
                coalesceEnabled,
                "",
                "",
                "all",
                true,
                2000,
                2,
                true,
                1,
                8,
                50,
                6,
                500,
                1800
        );
    }
}
