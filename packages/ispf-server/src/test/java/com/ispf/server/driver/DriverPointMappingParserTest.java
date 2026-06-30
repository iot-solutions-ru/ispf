package com.ispf.server.driver;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DriverPointMappingParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesLegacyStringMappings() {
        Map<String, DriverPointMappingParser.Entry> parsed = DriverPointMappingParser.parse(
                "{\"temperature\":\"HOLDING:1:40001\",\"status\":\"COIL:1:0\"}",
                objectMapper
        );

        assertThat(parsed.get("temperature").pointId()).isEqualTo("HOLDING:1:40001");
        assertThat(parsed.get("temperature").haystackTags()).isEmpty();
        assertThat(DriverPointMappingParser.toPointIds(parsed)).containsEntry("status", "COIL:1:0");
    }

    @Test
    void parsesExtendedHaystackObjectMappings() {
        Map<String, DriverPointMappingParser.Entry> parsed = DriverPointMappingParser.parse("""
                {
                  "sineWave": {
                    "point": "sim",
                    "haystackTags": ["point", "sensor", "temp"],
                    "unit": "°C",
                    "dis": "Sine wave"
                  },
                  "status": "sim"
                }
                """, objectMapper);

        DriverPointMappingParser.Entry sine = parsed.get("sineWave");
        assertThat(sine.pointId()).isEqualTo("sim");
        assertThat(sine.haystackTags()).containsExactly("point", "sensor", "temp");
        assertThat(sine.unit()).isEqualTo("°C");
        assertThat(sine.dis()).isEqualTo("Sine wave");
        assertThat(parsed.get("status").pointId()).isEqualTo("sim");
    }

    @Test
    void acceptsAddressAliasAndTagsField() {
        Map<String, DriverPointMappingParser.Entry> parsed = DriverPointMappingParser.parse("""
                {
                  "presentValue": {
                    "address": "analog-value:1:present-value",
                    "tags": ["point", "cur"]
                  }
                }
                """, objectMapper);

        assertThat(parsed.get("presentValue").pointId()).isEqualTo("analog-value:1:present-value");
        assertThat(parsed.get("presentValue").haystackTags()).containsExactly("point", "cur");
    }
}
