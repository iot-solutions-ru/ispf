package com.ispf.server.api.support;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectChildPageTest {

    @Test
    void leafGlobMatchesTheObjectName() {
        assertTrue(ObjectChildPage.leafMatches("root.platform.devices.modbus-1", "modbus*"));
        assertTrue(ObjectChildPage.leafMatches("root.platform.devices.Modbus-1", "modbus*"));
        assertTrue(ObjectChildPage.leafMatches("root.platform.devices.sensor-a", "sensor-?"));
        assertFalse(ObjectChildPage.leafMatches("root.platform.devices.modbus-1", "opc*"));
        assertTrue(ObjectChildPage.leafMatches("root.platform.devices.a(b)", "a(b)"));
        assertTrue(ObjectChildPage.leafMatches("root.devices.pump", "  "));
    }

    @Test
    void compiledNameGlobCanBeReusedAcrossLeaves() {
        var pattern = ObjectChildPage.compileNameGlob("modbus*");
        assertTrue(ObjectChildPage.leafMatches("root.platform.devices.modbus-1", pattern));
        assertTrue(ObjectChildPage.leafMatches("root.platform.devices.modbus-2", pattern));
        assertFalse(ObjectChildPage.leafMatches("root.platform.devices.opc-1", pattern));
    }

    @Test
    void omittedLimitKeepsTheFullList() {
        List<String> items = List.of("a", "b", "c");
        assertEquals(items, ObjectChildPage.slice(items, 0, null));
    }

    @Test
    void sliceReturnsTheRequestedWindow() {
        List<String> items = List.of("a", "b", "c", "d", "e");
        assertEquals(List.of("c", "d"), ObjectChildPage.slice(items, 2, 2));
        assertEquals(List.of("e"), ObjectChildPage.slice(items, 4, 50));
        assertEquals(List.of(), ObjectChildPage.slice(items, 9, 10));
        assertEquals(2, ObjectChildPage.slice(items, -4, 2).size());
        assertEquals(ObjectChildPage.MAX_LIMIT, ObjectChildPage.slice(
                java.util.stream.IntStream.range(0, 800).boxed().toList(),
                0,
                5000
        ).size());
    }
}
