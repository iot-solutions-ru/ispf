package com.ispf.server.driver;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriverWriteFenceTest {

    private final DriverWriteFence fence = new DriverWriteFence();

    @Test
    void allowsWriteWhenEpochMatchesAndPositive() {
        assertTrue(fence.allowWrite(3, 3));
    }

    @Test
    void rejectsStaleCommandEpoch() {
        assertFalse(fence.allowWrite(2, 3));
    }

    @Test
    void rejectsZeroCurrentEpoch() {
        assertFalse(fence.allowWrite(0, 0));
    }

    @Test
    void rejectsCommandAheadOfCurrentEpoch() {
        assertFalse(fence.allowWrite(5, 4));
    }
}
