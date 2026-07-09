package com.ispf.server.platform.analytics.catalog;

import com.ispf.server.bootstrap.LabBlueprintBootstrap;
import com.ispf.server.driver.DriverPointMappingParser;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AnalyticsTagHaystackMappingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesDerivedValueHaystackMappingForVirtualLabDevice() throws Exception {
        Map<String, DriverPointMappingParser.Entry> mappings = Map.of(
                "sineWave",
                DriverPointMappingParser.parse(LabBlueprintBootstrap.LAB_POINT_MAPPINGS, objectMapper).get("sineWave"),
                "derivedValue",
                new DriverPointMappingParser.Entry("", List.of("point", "cur", "his"), "", "Sensor A")
        );

        String json = serialize(mappings);
        assertThat(json).contains("derivedValue");
        assertThat(json).contains("haystackTags");
    }

    @Test
    void serializesDerivedValueWhenDriverMappingsWereClearedByConfigure() {
        Map<String, DriverPointMappingParser.Entry> mappings = Map.of(
                "derivedValue",
                new DriverPointMappingParser.Entry("", List.of("point", "cur", "his"), "", "Sensor A")
        );

        assertThatCode(() -> serialize(mappings)).doesNotThrowAnyException();
    }

    private String serialize(Map<String, DriverPointMappingParser.Entry> mappings) throws Exception {
        Map<String, Object> serialized = new LinkedHashMap<>();
        for (Map.Entry<String, DriverPointMappingParser.Entry> item : mappings.entrySet()) {
            DriverPointMappingParser.Entry entry = item.getValue();
            Map<String, Object> payload = new LinkedHashMap<>();
            if (!entry.pointId().isBlank()) {
                payload.put("point", entry.pointId());
            }
            if (!entry.haystackTags().isEmpty()) {
                payload.put("haystackTags", entry.haystackTags());
            }
            if (!entry.unit().isBlank()) {
                payload.put("unit", entry.unit());
            }
            if (!entry.dis().isBlank()) {
                payload.put("dis", entry.dis());
            }
            serialized.put(item.getKey(), payload.isEmpty() ? entry.pointId() : payload);
        }
        return objectMapper.writeValueAsString(serialized);
    }
}
