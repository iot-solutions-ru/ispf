package com.ispf.expression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CounterRateBindingTest {

    @Test
    void matchesCounterRateSyntax() {
        assertTrue(CounterRateBinding.INSTANCE.matches("counterRate(ifInOctets)"));
        assertTrue(CounterRateBinding.INSTANCE.matches("counterRate(ifOutOctets, 4294967296, value)"));
    }

    @Test
    void counterDeltaHandlesWrapAndReset() {
        assertEquals(500d, CounterRateBinding.counterDelta(1500, 1000, CounterRateBinding.DEFAULT_COUNTER_MAX));
        assertEquals(
                150d,
                CounterRateBinding.counterDelta(
                        50,
                        CounterRateBinding.DEFAULT_COUNTER_MAX - 100,
                        CounterRateBinding.DEFAULT_COUNTER_MAX
                )
        );
        assertNull(CounterRateBinding.counterDelta(1000, 3_000_000_000d, CounterRateBinding.DEFAULT_COUNTER_MAX));
    }
}
