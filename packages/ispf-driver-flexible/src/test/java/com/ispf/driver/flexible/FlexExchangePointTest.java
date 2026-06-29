package com.ispf.driver.flexible;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlexExchangePointTest {

    @Test
    void detectsPipelineMappings() {
        assertTrue(FlexExchangePoint.isPipeline("req:\\x01ABC|extract:literal:1"));
        assertFalse(FlexExchangePoint.isPipeline("READ:TEMP=([0-9.]+)"));
    }

    @Test
    void parsesPipelineWithVars() {
        FlexExchangePoint point = FlexExchangePoint.parse(
                "req:\\x01${securityCode}i201${tank}|var:tank=01|verifyChecksum|extract:asciiHexFloat:0:after:07");
        assertEquals("\\x01${securityCode}i201${tank}", point.requestTemplate());
        assertEquals("01", point.variables().get("tank"));
        assertTrue(point.verifyChecksum());
    }

    @Test
    void groupsPointsByResolvedRequest() {
        Map<String, String> mappings = Map.of(
                "volume", "req:\\x01i20101|extract:asciiHexFloat:0:after:07",
                "height", "req:\\x01i20101|extract:asciiHexFloat:3:after:07",
                "legacy", "READ:TEMP=([0-9.]+)"
        );
        Map<String, java.util.List<Map.Entry<String, FlexExchangePoint>>> groups =
                FlexExchangePoint.groupByRequest(mappings, Map.of("securityCode", ""));
        assertEquals(1, groups.size());
        assertEquals(2, groups.values().iterator().next().size());
    }
}
