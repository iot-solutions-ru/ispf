package com.ispf.expression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void findsAllNewBindings() {
        assertTrue(PlatformBindingRegistry.matches("rate(gauge)"));
        assertTrue(PlatformBindingRegistry.matches("counterDelta(ifInOctets)"));
        assertTrue(PlatformBindingRegistry.matches("movingAvg(gauge, 60)"));
        assertTrue(PlatformBindingRegistry.matches("movingMin(gauge, 30)"));
        assertTrue(PlatformBindingRegistry.matches("movingMax(gauge, 30)"));
        assertTrue(PlatformBindingRegistry.matches("deadband(gauge, 1.0)"));
        assertTrue(PlatformBindingRegistry.matches("hysteresis(gauge, 80, 70)"));
        assertTrue(PlatformBindingRegistry.matches("unitConvert(temperature, C, F)"));
        assertTrue(PlatformBindingRegistry.matches("read(\"root.platform.devices.foo/temperature\")"));
        assertTrue(PlatformBindingRegistry.matches("call(@/fn/myFunc)"));
        assertTrue(PlatformBindingRegistry.matches("call(@/fn/myFunc, @/inputVar)"));
        assertTrue(PlatformBindingRegistry.matches("call(root.remote/fn/myFunc)"));
        assertTrue(PlatformBindingRegistry.matches("call(root.remote/fn/myFunc, @/inputVar)"));
        assertTrue(PlatformBindingRegistry.matches("sumRecordField(table, int)"));
        assertTrue(PlatformBindingRegistry.matches("sumRecordField(table, \"int\")"));
    }

    @Test
    void callMatchesFunctionRefOnly() {
        assertTrue(CallRefBinding.INSTANCE.matches("call(@/fn/fn)"));
        assertFalse(CallRefBinding.INSTANCE.matches("read(\"root/foo/bar\")"));
    }
}
