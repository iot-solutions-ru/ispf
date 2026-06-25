package com.ispf.server.driver;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parsed driver attachment stored on a device object.
 */
public record DriverBinding(
        String driverId,
        int pollIntervalMs,
        Map<String, String> configuration,
        Map<String, String> pointMappings,
        TelemetryPublishMode telemetryPublishMode,
        int telemetryCoalesceMs
) {
    public static final String DEFAULT_DRIVER_ID = "virtual";
    private static final String KEY_PUBLISH_MODE = "telemetryPublishMode";
    private static final String KEY_COALESCE_MS = "telemetryCoalesceMs";

    public DriverBinding {
        if (telemetryPublishMode == null) {
            telemetryPublishMode = TelemetryPublishMode.FULL;
        }
        configuration = configuration != null ? Map.copyOf(configuration) : Map.of();
        pointMappings = pointMappings != null ? Map.copyOf(pointMappings) : Map.of();
    }

    public static DriverBinding of(
            String driverId,
            int pollIntervalMs,
            Map<String, String> configuration,
            Map<String, String> pointMappings
    ) {
        return of(driverId, pollIntervalMs, configuration, pointMappings, null, 0);
    }

    public static DriverBinding of(
            String driverId,
            int pollIntervalMs,
            Map<String, String> configuration,
            Map<String, String> pointMappings,
            TelemetryPublishMode telemetryPublishMode,
            int telemetryCoalesceMs
    ) {
        Map<String, String> driverConfig = new LinkedHashMap<>(configuration != null ? configuration : Map.of());
        TelemetryPublishMode resolvedMode = telemetryPublishMode != null
                ? telemetryPublishMode
                : TelemetryPublishMode.parse(driverConfig.remove(KEY_PUBLISH_MODE));
        int resolvedCoalesce = telemetryCoalesceMs > 0
                ? telemetryCoalesceMs
                : parsePositiveInt(driverConfig.remove(KEY_COALESCE_MS));
        return new DriverBinding(
                driverId,
                pollIntervalMs,
                Map.copyOf(driverConfig),
                pointMappings,
                resolvedMode,
                resolvedCoalesce
        );
    }

    public static DriverBinding virtualDemo() {
        return of(
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

    public long effectiveCoalesceMs(long globalDefaultMs) {
        return telemetryCoalesceMs > 0 ? telemetryCoalesceMs : globalDefaultMs;
    }

    public Map<String, String> configurationForDriver() {
        return configuration;
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
        Map<String, String> config = new LinkedHashMap<>(parseMap(configJson, objectMapper));
        TelemetryPublishMode mode = TelemetryPublishMode.parse(config.remove(KEY_PUBLISH_MODE));
        int coalesceOverride = parsePositiveInt(config.remove(KEY_COALESCE_MS));
        Map<String, String> mappings = parseMap(pointMappingsJson, objectMapper);
        return of(resolvedDriverId, interval, Map.copyOf(config), mappings, mode, coalesceOverride);
    }

    public Map<String, String> configurationWithPolicy() {
        Map<String, String> merged = new LinkedHashMap<>(configuration);
        if (telemetryPublishMode != TelemetryPublishMode.FULL) {
            merged.put(KEY_PUBLISH_MODE, telemetryPublishMode.name());
        } else {
            merged.remove(KEY_PUBLISH_MODE);
        }
        if (telemetryCoalesceMs > 0) {
            merged.put(KEY_COALESCE_MS, Integer.toString(telemetryCoalesceMs));
        } else {
            merged.remove(KEY_COALESCE_MS);
        }
        return Map.copyOf(merged);
    }

    private static int parsePositiveInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
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
