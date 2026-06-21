package com.ispf.expression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformBindingRegistryTest {

    @Test
    void findsAllPhaseOneBindings() {
        assertTrue(PlatformBindingRegistry.matches("counterRate(ifInOctets)"));
        assertTrue(PlatformBindingRegistry.matches("selectField(temperature)"));
        assertTrue(PlatformBindingRegistry.matches("scale(temperature, 0, 100, 0, 1)"));
        assertTrue(PlatformBindingRegistry.matches("clamp(temperature, 0, 50)"));
        assertTrue(PlatformBindingRegistry.matches("format(\"%.1f\", temperature)"));
        assertTrue(PlatformBindingRegistry.matches("delta(counter)"));
    }
}
