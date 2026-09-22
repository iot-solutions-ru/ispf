package com.ispf.driver.nmea;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NmeaParserTest {

    /** Handwritten NMEA 0183 GGA; XOR of body between $ and * is 0x47. */
    private static final String GGA_VALID =
            "$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47";

    @Test
    void parsesGgaSentenceWithValidChecksum() {
        Map<String, String> fields = NmeaParser.parseSentenceFields(GGA_VALID);
        assertEquals("GPGGA", fields.get("type"));
        assertEquals("123519", fields.get("f1"));
        assertEquals("4807.038", fields.get("f2"));
        assertEquals("1", fields.get("f6"));
    }

    @Test
    void rejectsGgaSentenceWithWrongChecksum() {
        String bad = "$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*00";
        Map<String, String> fields = NmeaParser.parseSentenceFields(bad);
        assertTrue(fields.isEmpty(), "wrong checksum must not parse as a valid fix");
        assertFalse(NmeaParser.checksumOk(bad));
        assertTrue(NmeaParser.checksumOk(GGA_VALID));
    }

    @Test
    void serializesToJson() {
        Map<String, String> fields = Map.of("type", "GPRMC", "f1", "123519");
        String json = NmeaParser.toJson(fields);
        assertTrue(json.contains("\"type\":\"GPRMC\""));
        assertTrue(json.contains("\"f1\":\"123519\""));
    }

    @Test
    void metadataDescriptionOmitsLab() {
        String description = new NmeaDeviceDriver().metadata().description();
        assertFalse(description.toLowerCase(Locale.ROOT).contains("lab"));
    }
}
