package com.ispf.server.driver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Parsed driver attachment stored on a device object.
 */
public record DriverBinding(
        String driverId,
        int pollIntervalMs,
        Map<String, String> configuration,
        Map<String, String> pointMappings
) {
    public static final String DEFAULT_DRIVER_ID = "virtual";

    public static DriverBinding virtualDemo() {
        return new DriverBinding(
                "virtual",
                2000,
                Map.of(
                        "baseTemperature", "22.0",
                        "amplitude", "15.0",
                        "periodSec", "60"
                ),
                Map.of("temperature", "sim")
        );
    }

    public static DriverBinding parse(
            String driverId,
            int pollIntervalMs,
            String configJson,
            String pointMappingsJson,
            ObjectMapper objectMapper
    ) {
        String resolvedDriverId = driverId == null || driverId.isBlank() ? DEFAULT_DRIVER_ID : driverId;
        int interval = pollIntervalMs > 0 ? pollIntervalMs : 2000;
        Map<String, String> config = parseMap(configJson, objectMapper);
        Map<String, String> mappings = parseMap(pointMappingsJson, objectMapper);
        return new DriverBinding(resolvedDriverId, interval, config, mappings);
    }

    private static Map<String, String> parseMap(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }
}
