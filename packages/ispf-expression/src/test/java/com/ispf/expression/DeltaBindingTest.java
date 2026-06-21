package com.ispf.expression;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DeltaBindingTest {

    @BeforeEach
    void resetState() {
        PlatformBindingRegistry.clearStateForTests();
    }

    @Test
    void matchesSyntax() {
        assertTrue(DeltaBinding.INSTANCE.matches("delta(counter)"));
        assertTrue(DeltaBinding.INSTANCE.matches("delta(counter, value)"));
    }
}
