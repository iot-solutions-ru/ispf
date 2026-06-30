package com.ispf.driver.haystack;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HaystackJsonGridTest {

    @Test
    void buildsReadRequestForRefs() {
        String json = HaystackJsonGrid.buildReadRequest(List.of("site.equip.temp", "site.equip.status"));
        assertTrue(json.contains("\"site.equip.temp\""));
        assertTrue(json.contains("\"site.equip.status\""));
        assertTrue(json.contains("\"_kind\":\"grid\""));
    }

    @Test
    void parsesReadResponseGrid() {
        String body = """
                {
                  "_kind": "grid",
                  "meta": {"ver": "3.0"},
                  "cols": [{"name": "id"}, {"name": "curVal"}, {"name": "unit"}, {"name": "dis"}],
                  "rows": [
                    {
                      "id": {"_kind": "ref", "val": "site.equip.temp"},
                      "curVal": 22.5,
                      "unit": "°C",
                      "dis": "Supply temp"
                    }
                  ]
                }
                """;

        Map<String, HaystackJsonGrid.HaystackPointValue> parsed = HaystackJsonGrid.parseReadResponse(body);
        assertEquals(1, parsed.size());
        HaystackJsonGrid.HaystackPointValue value = parsed.get("site.equip.temp");
        assertEquals(22.5, ((Number) value.curVal()).doubleValue(), 0.001);
        assertEquals("°C", value.unit());
        assertEquals("Supply temp", value.dis());
    }
}
