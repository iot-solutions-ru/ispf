package com.ispf.driver.haystack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HaystackPointTest {

    @Test
    void parsesRefWithOptionalAtPrefix() {
        assertEquals("site.equip.temp", HaystackPoint.parse("@site.equip.temp").ref());
        assertEquals("site.equip.temp", HaystackPoint.parse("site.equip.temp").ref());
    }

    @Test
    void rejectsBlankMapping() {
        assertThrows(IllegalArgumentException.class, () -> HaystackPoint.parse("  "));
        assertThrows(IllegalArgumentException.class, () -> HaystackPoint.parse("@"));
    }
}
