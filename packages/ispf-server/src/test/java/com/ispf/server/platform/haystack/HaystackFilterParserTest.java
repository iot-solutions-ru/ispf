package com.ispf.server.platform.haystack;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HaystackFilterParserTest {

    @Test
    void parsesSingleMarker() {
        assertEquals(List.of("temp"), HaystackFilterParser.parseRequiredMarkers("temp"));
    }

    @Test
    void parsesAndConjunction() {
        assertEquals(
                List.of("point", "temp"),
                HaystackFilterParser.parseRequiredMarkers("point and temp")
        );
        assertEquals(
                List.of("equip", "ahu"),
                HaystackFilterParser.parseRequiredMarkers("equip  AND   ahu")
        );
    }

    @Test
    void rejectsEmptyFilter() {
        assertThrows(ResponseStatusException.class, () -> HaystackFilterParser.parseRequiredMarkers("  "));
    }

    @Test
    void rejectsInvalidToken() {
        assertThrows(ResponseStatusException.class, () -> HaystackFilterParser.parseRequiredMarkers("point and 2temp"));
    }

    @Test
    void roundTripsFilterString() {
        List<String> markers = List.of("equip", "point", "temp");
        assertEquals("equip and point and temp", HaystackFilterParser.toFilterString(markers));
    }
}
