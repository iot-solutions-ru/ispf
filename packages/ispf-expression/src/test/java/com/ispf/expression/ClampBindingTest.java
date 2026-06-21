package com.ispf.expression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClampBindingTest {

    @Test
    void matchesSyntax() {
        assertTrue(ClampBinding.INSTANCE.matches("clamp(temperature, 0, 50)"));
        assertTrue(ClampBinding.INSTANCE.matches("clamp(source, -10, 10, value)"));
    }

    @Test
    void clampLimitsValue() {
        assertEquals(0, ClampBinding.clamp(-5, 0, 50));
        assertEquals(50, ClampBinding.clamp(95, 0, 50));
        assertEquals(22.5, ClampBinding.clamp(22.5, 0, 50));
    }
}
