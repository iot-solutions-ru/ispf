package com.ispf.server.ai.agent;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.server.bootstrap.LabBlueprintBootstrap;
import com.ispf.server.driver.DeviceProvisioningService;
import com.ispf.server.driver.DriverBinding;
import com.ispf.server.driver.DriverRuntimeService;
import com.ispf.server.object.ObjectTreePort;
import com.ispf.server.object.ObjectTemplateService;
import com.ispf.server.security.acl.ObjectAccessService;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One-shot virtual device provisioning for the tree-first agent (OOTB, no profiles).
 */
final class AgentVirtualDeviceTools {

    private AgentVirtualDeviceTools() {
    }

    static List<PlatformAgentTool> all(
            ObjectTreePort ObjectTreePort,
            ObjectAccessService objectAccessService,
            ObjectTemplateService objectTemplateService,
            DeviceProvisioningService deviceProvisioningService,
            DriverRuntimeService driverRuntimeService,
            LabBlueprintBootstrap LabBlueprintBootstrap,
            BlueprintRegistry BlueprintRegistry,
            ObjectMapper objectMapper
    ) {
        return List.of(
                createVirtualDeviceTool(
                        ObjectTreePort,
                        objectAccessService,
                        objectTemplateService,
                        deviceProvisioningService,
                        driverRuntimeService,
                        LabBlueprintBootstrap,
                        BlueprintRegistry,
                        objectMapper
                ),
                describeVirtualDriverTool(BlueprintRegistry)
        );
    }

    private static PlatformAgentTool describeVirtualDriverTool(BlueprintRegistry BlueprintRegistry) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "list_virtual_profiles";
            }

            @Override
            public String description() {
                return "Describe out-of-the-box virtual driver (no profiles). "
                        + "Prefer create_virtual_device for simulators. "
                        + "Domain plants: list_relative_blueprints + apply_relative_blueprint.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                boolean registered = BlueprintRegistry.findByName(VirtualDeviceDefaults.TEMPLATE_ID).isPresent();
                return Map.of(
                        "status", "OK",
                        "driverId", "virtual",
                        "profiles", List.of(),
                        "defaults", VirtualDeviceDefaults.catalogRow(registered),
                        "hint", "No profiles вЂ” one OOTB virtual device. Domain enrichment via relative blueprints."
                );
            }
        };
    }

    private static PlatformAgentTool createVirtualDeviceTool(
            ObjectTreePort ObjectTreePort,
            ObjectAccessService objectAccessService,
            ObjectTemplateService objectTemplateService,
            DeviceProvisioningService deviceProvisioningService,
            DriverRuntimeService driverRuntimeService,
            LabBlueprintBootstrap LabBlueprintBootstrap,
            BlueprintRegistry BlueprintRegistry,
            ObjectMapper objectMapper
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "create_virtual_device";
            }

            @Override
            public String description() {
                return "Create DEVICE with out-of-the-box virtual driver (multi-type telemetry), model "
                        + VirtualDeviceDefaults.TEMPLATE_ID + ", start driver. "
                        + "Args: parentPath, name, displayName, pollIntervalMs?, autoStart? (default true). "
                        + "Ignore legacy profile arg if present. Call list_variables before finish. "
                        + "Domain plants: apply_relative_blueprint instead of a driver profile.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String parentPath = stringArg(arguments, "parentPath");
                String name = stringArg(arguments, "name");
                String displayName = stringArg(arguments, "displayName");
                if (parentPath.isBlank() || name.isBlank() || displayName.isBlank()) {
                    return Map.of(
                            "status", "ERROR",
                            "error", "parentPath, name, displayName are required"
                    );
                }
                LabBlueprintBootstrap.ensureLabModels();
                if (BlueprintRegistry.findByName(VirtualDeviceDefaults.TEMPLATE_ID).isEmpty()) {
                    return Map.of(
                            "status", "ERROR",
                            "error", "Model not registered: " + VirtualDeviceDefaults.TEMPLATE_ID
                                    + " вЂ” server bootstrap issue; contact admin",
                            "templateId", VirtualDeviceDefaults.TEMPLATE_ID
                    );
                }
                if (ObjectTreePort.tree().findByPath(parentPath).isEmpty()) {
                    return Map.of(
                            "status", "ERROR",
                            "error", "Parent not found: " + parentPath,
                            "hint", "Call list_objects parent=<existing folder> first; use parentPath from tool results."
                    );
                }
                var auth = context.authentication();
                objectAccessService.requireWrite(parentPath, auth);
                String fullPath = ObjectTreePort.tree().resolveChildPath(parentPath, name);
                if (ObjectTreePort.tree().findByPath(fullPath).isPresent()) {
                    return Map.of(
                            "status", "ERROR",
                            "error", "Object exists: " + fullPath,
                            "hint", "Reuse: get_object path=" + fullPath + " and list_variables.",
                            "existingPath", fullPath
                    );
                }

                try {
                    PlatformObject node = ObjectTreePort.create(
                            parentPath,
                            name,
                            ObjectType.DEVICE,
                            displayName,
                            null,
                            VirtualDeviceDefaults.TEMPLATE_ID
                    );
                    objectTemplateService.applyTemplate(node.path(), VirtualDeviceDefaults.TEMPLATE_ID);
                    int pollMs = intArg(arguments, "pollIntervalMs", 2000);
                    deviceProvisioningService.provisionDriver(node.path(), "virtual", pollMs, false);

                    Map<String, String> configuration = parseConfigJson(
                            objectMapper, VirtualDeviceDefaults.DRIVER_CONFIG
                    );
                    Map<String, String> pointMappings = parseConfigJson(
                            objectMapper, VirtualDeviceDefaults.POINT_MAPPINGS
                    );
                    driverRuntimeService.configure(
                            node.path(),
                            DriverBinding.of("virtual", pollMs, configuration, pointMappings)
                    );

                    boolean autoStart = boolArg(arguments, "autoStart", true);
                    DriverRuntimeService.DriverRuntimeStatus runtimeStatus = null;
                    if (autoStart) {
                        runtimeStatus = driverRuntimeService.start(node.path());
                        driverRuntimeService.pollNow(node.path());
                    }
                    ObjectTreePort.persistNodeTree(node.path());

                    PlatformObject saved = ObjectTreePort.require(node.path());
                    List<Map<String, Object>> variables = saved.variables().values().stream()
                            .map(AgentVirtualDeviceTools::variablePreview)
                            .toList();
                    long telemetryCount = variables.stream()
                            .filter(row -> !isDriverMeta(String.valueOf(row.get("name"))))
                            .count();

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("status", "OK");
                    result.put("path", node.path());
                    result.put("templateId", VirtualDeviceDefaults.TEMPLATE_ID);
                    result.put("driverId", "virtual");
                    result.put("variableCount", variables.size());
                    result.put("telemetryVariableCount", telemetryCount);
                    result.put("expectedVariables", VirtualDeviceDefaults.EXPECTED_VARIABLES);
                    result.put("variables", variables);
                    if (runtimeStatus != null) {
                        result.put("driverStatus", runtimeStatus.status());
                        result.put("connected", runtimeStatus.connected());
                    }
                    if (telemetryCount < 1) {
                        result.put("warning", "No telemetry variables yet вЂ” run driver_control action=poll or list_variables");
                    }
                    return result;
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static Map<String, String> parseConfigJson(ObjectMapper objectMapper, String json) throws Exception {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        Map<String, Object> raw = objectMapper.readValue(json, Map.class);
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            out.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return out;
    }

    private static boolean isDriverMeta(String name) {
        return name.startsWith("driver") || "timeZone".equals(name);
    }

    private static Map<String, Object> variablePreview(Variable variable) {
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("name", variable.name());
        preview.put("writable", variable.writable());
        variable.value().ifPresent(record -> {
            if (!record.rows().isEmpty()) {
                preview.put("value", record.firstRow());
            }
        });
        return preview;
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static int intArg(Map<String, Object> args, String key, int defaultValue) {
        Object value = args.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static boolean boolArg(Map<String, Object> args, String key, boolean defaultValue) {
        Object value = args.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value != null) {
            return Boolean.parseBoolean(String.valueOf(value).trim());
        }
        return defaultValue;
    }
}
