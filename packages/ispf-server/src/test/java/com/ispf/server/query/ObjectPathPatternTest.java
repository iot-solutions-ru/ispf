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

    @Test
    void extractsStaticPrefixBeforeFirstWildcard() {
        org.junit.jupiter.api.Assertions.assertEquals(
                "root.platform.devices.lab-",
                ObjectPathPattern.staticPrefix("root.platform.devices.lab-*")
        );
        org.junit.jupiter.api.Assertions.assertEquals(
                "root.platform.devices.",
                ObjectPathPattern.staticPrefix("root.platform.devices.**")
        );
        org.junit.jupiter.api.Assertions.assertEquals(
                "root.platform.devices.pump-01",
                ObjectPathPattern.staticPrefix("root.platform.devices.pump-01")
        );
        org.junit.jupiter.api.Assertions.assertEquals("", ObjectPathPattern.staticPrefix("**"));
    }

    @Test
    void quotesRegexMetacharactersInLiteralSegments() {
        assertTrue(ObjectPathPattern.matches("root.a+b1", "root.a+b*"));
        assertFalse(ObjectPathPattern.matches("root.ab1", "root.a+b*"));
        assertFalse(ObjectPathPattern.matches("root.xx", "root.x+*"));
        assertTrue(ObjectPathPattern.matches("root.devices.(lab)1", "root.devices.(lab)*"));
        assertFalse(ObjectPathPattern.matches("root.devices.lab1", "root.devices.(lab)*"));
    }
}
