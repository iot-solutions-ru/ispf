package com.ispf.driver.nmea;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NmeaParserTest {

    @Test
    void parsesGgaSentence() {
        Map<String, String> fields = NmeaParser.parseSentenceFields(
                "$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47");
        assertEquals("GPGGA", fields.get("type"));
        assertEquals("123519", fields.get("f1"));
        assertEquals("4807.038", fields.get("f2"));
    }

    @Test
    void serializesToJson() {
        Map<String, String> fields = Map.of("type", "GPRMC", "f1", "123519");
        String json = NmeaParser.toJson(fields);
        assertTrue(json.contains("\"type\":\"GPRMC\""));
        assertTrue(json.contains("\"f1\":\"123519\""));
    }
}
