package com.ispf.expression;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaleBindingTest {

    @Test
    void matchesSyntax() {
        assertTrue(ScaleBinding.INSTANCE.matches("scale(temperature, 0, 100, 0, 1)"));
        assertTrue(ScaleBinding.INSTANCE.matches("scale(source, -20, 50, 0, 100, value)"));
    }

    @Test
    void scaleLinearMap() {
        Optional<Double> result = ScaleBinding.scale(25, 0, 100, 0, 1);
        assertEquals(0.25, result.orElseThrow(), 0.0001);
    }

    @Test
    void scaleReturnsEmptyWhenInputRangeZero() {
        assertTrue(ScaleBinding.scale(10, 5, 5, 0, 1).isEmpty());
    }
}
