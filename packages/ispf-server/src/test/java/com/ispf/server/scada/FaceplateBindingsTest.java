package com.ispf.server.scada;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FaceplateBindingsTest {

    private static final String ROOT = "root.platform.devices.pump-01";

    @Test
    void joinsRelativeSegmentUnderRoot() {
        assertEquals(
                "root.platform.devices.pump-01.variables.speed",
                FaceplateBindings.resolve(ROOT, "variables.speed")
        );
    }

    @Test
    void acceptsAbsolutePathUnderRoot() {
        assertEquals(
                "root.platform.devices.pump-01",
                FaceplateBindings.resolve(ROOT, "root.platform.devices.pump-01")
        );
    }

    @Test
    void rejectsAbsolutePathOutsideRoot() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FaceplateBindings.resolve(ROOT, "root.platform.devices.pump-02")
        );
    }

    @Test
    void rejectsParentEscapeSegment() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FaceplateBindings.resolve(ROOT, "..sibling")
        );
    }
}
