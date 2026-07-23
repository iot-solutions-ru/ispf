package com.ispf.server.ai.agent;

import com.ispf.server.bootstrap.LabBlueprintBootstrap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Defaults for out-of-the-box virtual devices (no driver profiles).
 * Domain plants use mixin blueprints + {@code create_object} / {@code apply_mixin_blueprint}.
 */
final class VirtualDeviceDefaults {

    static final String TEMPLATE_ID = LabBlueprintBootstrap.VIRTUAL_UNIFIED_MODEL;
    static final String DRIVER_CONFIG = LabBlueprintBootstrap.UNIFIED_DRIVER_CONFIG;
    static final String POINT_MAPPINGS = LabBlueprintBootstrap.UNIFIED_POINT_MAPPINGS;
    static final List<String> EXPECTED_VARIABLES = List.of(
            "temperature", "pressure", "flowRate", "meterLiters", "sineWave", "sawtoothWave",
            "triangleWave", "status", "coordinates", "telemetryTable", "deviceHealth"
    );

    private VirtualDeviceDefaults() {
    }

    static Map<String, Object> catalogRow(boolean templateRegistered) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("driverId", "virtual");
        row.put("templateId", TEMPLATE_ID);
        row.put("expectedVariables", EXPECTED_VARIABLES);
        row.put("templateRegistered", templateRegistered);
        row.put(
                "hint",
                "create_virtual_device provisions OOTB multi-type telemetry. "
                        + "Domain models (mini-tec, tank-farm, OGP): list_mixin_blueprints + apply_mixin_blueprint."
        );
        return row;
    }
}
