package com.ispf.server.ai.agent;

import com.ispf.server.bootstrap.LabModelBootstrap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Maps virtual driver profiles to object model templates and default driver JSON.
 */
final class VirtualDeviceProfileCatalog {

    static final String METER_DRIVER_CONFIG =
            "{\"profile\":\"meter\",\"litersPerSecond\":\"120\",\"filling\":\"true\"}";

    static final String METER_POINT_MAPPINGS =
            "{\"meterLiters\":\"sim\",\"flowRate\":\"sim\",\"filling\":\"sim\"}";

    private static final Map<String, ProfileSpec> PROFILES = Map.ofEntries(
            Map.entry("lab", new ProfileSpec(
                    LabModelBootstrap.VIRTUAL_LAB_MODEL,
                    LabModelBootstrap.LAB_DRIVER_CONFIG,
                    LabModelBootstrap.LAB_POINT_MAPPINGS,
                    List.of("sineWave", "sawtoothWave", "triangleWave", "status")
            )),
            Map.entry("meter", new ProfileSpec(
                    LabModelBootstrap.VIRTUAL_UNIFIED_MODEL,
                    METER_DRIVER_CONFIG,
                    METER_POINT_MAPPINGS,
                    List.of("meterLiters", "flowRate", "filling")
            )),
            Map.entry("unified", new ProfileSpec(
                    LabModelBootstrap.VIRTUAL_UNIFIED_MODEL,
                    LabModelBootstrap.UNIFIED_DRIVER_CONFIG,
                    LabModelBootstrap.UNIFIED_POINT_MAPPINGS,
                    List.of("temperature", "pressure", "flowRate", "meterLiters", "sineWave", "status")
            )),
            Map.entry("demo", new ProfileSpec(
                    LabModelBootstrap.VIRTUAL_UNIFIED_MODEL,
                    "{\"profile\":\"demo\",\"baseTemperature\":\"22.0\",\"amplitude\":\"15.0\",\"periodSec\":\"60\"}",
                    "{\"temperature\":\"sim\",\"humidity\":\"sim\",\"pressure\":\"sim\"}",
                    List.of("temperature", "humidity", "pressure")
            ))
    );

    private VirtualDeviceProfileCatalog() {
    }

    static Optional<ProfileSpec> resolve(String profile) {
        if (profile == null || profile.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(PROFILES.get(profile.trim().toLowerCase(Locale.ROOT)));
    }

    static List<String> profileNames() {
        return List.copyOf(PROFILES.keySet());
    }

    record ProfileSpec(
            String templateId,
            String driverConfigJson,
            String driverPointMappingsJson,
            List<String> expectedVariables
    ) {
    }

    static Map<String, Object> profileCatalogRow(String name, ProfileSpec spec) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("profile", name);
        row.put("templateId", spec.templateId());
        row.put("expectedVariables", spec.expectedVariables());
        return row;
    }
}
