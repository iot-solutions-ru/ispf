package com.ispf.server.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectPathPatternTest {

    @Test
    void matchesPrefixGlobAndDeepGlob() {
        assertTrue(ObjectPathPattern.matches(
                "root.platform.devices.lab-userA-01",
                "root.platform.devices.lab-"
        ));
        assertTrue(ObjectPathPattern.matches(
                "root.platform.devices.lab-userB-01",
                "root.platform.devices.lab-*"
        ));
        assertTrue(ObjectPathPattern.matches(
                "root.platform.devices.nested.child",
                "root.platform.devices.**"
        ));
        assertFalse(ObjectPathPattern.matches(
                "root.platform.devices.demo-sensor-01",
                "root.platform.devices.lab-*"
        ));
    }
}
