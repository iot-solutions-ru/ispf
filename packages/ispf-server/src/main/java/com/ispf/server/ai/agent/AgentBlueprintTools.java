package com.ispf.server.ai.agent;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.blueprint.BlueprintApplyResult;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintEngine;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.plugin.blueprint.BlueprintType;
import com.ispf.plugin.blueprint.SystemIntrinsicBlueprints;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.blueprint.BlueprintApplicationService;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Agent tools for RELATIVE mixins, INSTANCE types, and ABSOLUTE singleton hubs.
 */
final class AgentBlueprintTools {

    private AgentBlueprintTools() {
    }

    static List<PlatformAgentTool> all(
            BlueprintRegistry blueprintRegistry,
            BlueprintApplicationService blueprintApplicationService,
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return List.of(
                listRelativeBlueprintsTool(blueprintRegistry),
                listInstanceTypesTool(blueprintRegistry),
                listAbsoluteBlueprintsTool(blueprintRegistry),
                getObjectBlueprintTool(blueprintRegistry),
                applyRelativeBlueprintTool(
                        blueprintRegistry,
                        blueprintApplicationService,
                        objectManager,
                        objectAccessService,
                        tenantScopeService
                ),
                instantiateInstanceTypeTool(
                        blueprintRegistry,
                        blueprintApplicationService,
                        objectManager,
                        objectAccessService,
                        tenantScopeService
                ),
                ensureAbsoluteInstanceTool(
                        blueprintRegistry,
                        blueprintApplicationService,
                        objectAccessService,
                        tenantScopeService
                )
        );
    }

    private static PlatformAgentTool listRelativeBlueprintsTool(BlueprintRegistry blueprintRegistry) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "list_relative_blueprints";
            }

            @Override
            public String description() {
                return "List RELATIVE model blueprints (mixins) under root.platform.relative-blueprints. "
                        + "They add variables, events, functions to existing objects via apply_relative_blueprint. "
                        + "Optional query filter and targetObjectType (DEVICE, CUSTOM, …).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                return listBlueprintsByType(blueprintRegistry, BlueprintType.RELATIVE, arguments, true);
            }
        };
    }

    private static PlatformAgentTool listInstanceTypesTool(BlueprintRegistry blueprintRegistry) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "list_instance_types";
            }

            @Override
            public String description() {
                return "List INSTANCE model blueprints (object templates) under root.platform.instance-types. "
                        + "Use instantiate_instance_type or create_object with templateId. "
                        + "Optional platformType (DEVICE, CUSTOM, …), parentPath, query.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                Map<String, Object> result = listBlueprintsByType(blueprintRegistry, BlueprintType.INSTANCE, arguments, false);
                result.put(
                        "hint",
                        "Create with instantiate_instance_type parentPath=... instanceName=... blueprintName=<name>"
                );
                return result;
            }
        };
    }

    private static PlatformAgentTool listAbsoluteBlueprintsTool(BlueprintRegistry blueprintRegistry) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "list_absolute_blueprints";
            }

            @Override
            public String description() {
                return "List ABSOLUTE model blueprints (singleton hubs) under root.platform.absolute-blueprints. "
                        + "Each has one live instance under root.platform.instances.* — use ensure_absolute_instance.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                Map<String, Object> result = listBlueprintsByType(blueprintRegistry, BlueprintType.ABSOLUTE, arguments, false);
                result.put(
                        "hint",
                        "Use ensure_absolute_instance blueprintName=<name> — never instantiate twice (409 if exists)"
                );
                return result;
            }
        };
    }

    private static Map<String, Object> listBlueprintsByType(
            BlueprintRegistry blueprintRegistry,
            BlueprintType type,
            Map<String, Object> arguments,
            boolean excludeIntrinsic
    ) {
        String query = stringArg(arguments, "query").toLowerCase(Locale.ROOT);
        String targetFilter = stringArg(arguments, "targetObjectType").toUpperCase(Locale.ROOT);
        if (targetFilter.isBlank()) {
            targetFilter = stringArg(arguments, "platformType").toUpperCase(Locale.ROOT);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BlueprintDefinition model : blueprintRegistry.all()) {
            if (model.type() != type) {
                continue;
            }
            if (excludeIntrinsic && SystemIntrinsicBlueprints.isIntrinsic(model)) {
                continue;
            }
            if (!targetFilter.isBlank()
                    && !model.targetObjectType().name().equalsIgnoreCase(targetFilter)) {
                continue;
            }
            String haystack = (model.name() + " " + model.description()).toLowerCase(Locale.ROOT);
            if (!query.isBlank() && !haystack.contains(query)) {
                continue;
            }
            rows.add(blueprintSummary(model));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "OK");
        result.put("BlueprintType", type.name());
        result.put("count", rows.size());
        result.put("blueprints", rows);
        return result;
    }

    private static PlatformAgentTool getObjectBlueprintTool(BlueprintRegistry blueprintRegistry) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "get_object_blueprint";
            }

            @Override
            public String description() {
                return "Schema of a model blueprint: variables, events, functions, BlueprintType, targetObjectType. "
                        + "Args: blueprintName or blueprintId (e.g. virtual-lab-v1).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                Optional<BlueprintDefinition> model = resolveBlueprint(blueprintRegistry, arguments);
                if (model.isEmpty()) {
                    return Map.of(
                            "status", "ERROR",
                            "error", "blueprintName or blueprintId required; use list_relative_blueprints or list_object_blueprints"
                    );
                }
                return Map.of("status", "OK", "model", blueprintDetail(model.get()));
            }
        };
    }

    private static PlatformAgentTool applyRelativeBlueprintTool(
            BlueprintRegistry blueprintRegistry,
            BlueprintApplicationService blueprintApplicationService,
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "apply_relative_blueprint";
            }

            @Override
            public String description() {
                return "Attach a RELATIVE model mixin to an existing object — merges variables, events, functions, "
                        + "binding rules without changing object path. "
                        + "Args: objectPath (required), blueprintName or blueprintId (e.g. virtual-lab-v1). "
                        + "Prefer over manual create_variable when a catalog model fits. "
                        + "After apply on DEVICE: configure_driver + driver_control start, then list_variables.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String objectPath = stringArg(arguments, "objectPath");
                if (objectPath.isBlank()) {
                    objectPath = stringArg(arguments, "path");
                }
                if (objectPath.isBlank()) {
                    return Map.of("status", "ERROR", "error", "objectPath is required");
                }
                Optional<BlueprintDefinition> modelOpt = resolveBlueprint(blueprintRegistry, arguments);
                if (modelOpt.isEmpty()) {
                    return Map.of("status", "ERROR", "error", "blueprintName or blueprintId is required");
                }
                BlueprintDefinition model = modelOpt.get();
                if (model.type() != BlueprintType.RELATIVE) {
                    return Map.of(
                            "status", "ERROR",
                            "error", model.name() + " is " + model.type() + ", not RELATIVE. "
                                    + "Use instantiate_instance_type for INSTANCE or ensure_absolute_instance for ABSOLUTE."
                    );
                }
                if (SystemIntrinsicBlueprints.isIntrinsic(model)) {
                    return Map.of("status", "ERROR", "error", "System-intrinsic model cannot be applied via agent");
                }
                var auth = context.authentication();
                if (!tenantScopeService.isPathVisible(objectPath, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + objectPath);
                }
                objectAccessService.requireWrite(objectPath, auth);
                PlatformObject target = objectManager.require(objectPath);
                if (model.targetObjectType() != null && target.type() != model.targetObjectType()) {
                    return Map.of(
                            "status", "ERROR",
                            "error", "Model target " + model.targetObjectType()
                                    + " does not match object type " + target.type()
                    );
                }
                try {
                    int variablesBefore = target.variables().size();
                    BlueprintApplyResult applyResult = blueprintApplicationService.applyBlueprintWithRules(
                            model.id(),
                            objectPath
                    );
                    PlatformObject updated = objectManager.require(objectPath);
                    int variablesAfter = updated.variables().size();
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("status", "OK");
                    result.put("objectPath", objectPath);
                    result.put("blueprintName", model.name());
                    result.put("blueprintId", model.id());
                    result.put("variablesAdded", Math.max(0, variablesAfter - variablesBefore));
                    result.put("variableCount", variablesAfter);
                    result.put("attachmentId", applyResult.attachment().id());
                    if (!applyResult.warnings().isEmpty()) {
                        result.put("warnings", applyResult.warnings());
                    }
                    if (target.type() == ObjectType.DEVICE) {
                        result.put(
                                "nextSteps",
                                "configure_driver autoStart=true (or driver_control start), then list_variables"
                        );
                    }
                    return result;
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool instantiateInstanceTypeTool(
            BlueprintRegistry blueprintRegistry,
            BlueprintApplicationService blueprintApplicationService,
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "instantiate_instance_type";
            }

            @Override
            public String description() {
                return "Create a new object from an INSTANCE model blueprint. "
                        + "Args: parentPath (required), instanceName (required), blueprintName or blueprintId. "
                        + "Equivalent to create_object with templateId. After DEVICE: configure_driver + list_variables.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String parentPath = stringArg(arguments, "parentPath");
                if (parentPath.isBlank()) {
                    parentPath = stringArg(arguments, "path");
                }
                String instanceName = stringArg(arguments, "instanceName");
                if (instanceName.isBlank()) {
                    instanceName = stringArg(arguments, "name");
                }
                if (parentPath.isBlank() || instanceName.isBlank()) {
                    return Map.of("status", "ERROR", "error", "parentPath and instanceName are required");
                }
                Optional<BlueprintDefinition> modelOpt = resolveBlueprint(blueprintRegistry, arguments);
                if (modelOpt.isEmpty()) {
                    return Map.of("status", "ERROR", "error", "blueprintName or blueprintId is required");
                }
                BlueprintDefinition model = modelOpt.get();
                if (model.type() != BlueprintType.INSTANCE) {
                    return Map.of(
                            "status", "ERROR",
                            "error", model.name() + " is " + model.type()
                                    + ". Use apply_relative_blueprint for RELATIVE or ensure_absolute_instance for ABSOLUTE."
                    );
                }
                var auth = context.authentication();
                if (!tenantScopeService.isPathVisible(parentPath, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + parentPath);
                }
                objectAccessService.requireWrite(parentPath, auth);
                try {
                    BlueprintApplyResult result = blueprintApplicationService.instantiateWithRules(
                            model.id(),
                            parentPath,
                            instanceName,
                            Map.of()
                    );
                    String fullPath = objectManager.tree().resolveChildPath(parentPath, instanceName);
                    PlatformObject instance = objectManager.require(fullPath);
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("status", "OK");
                    response.put("path", fullPath);
                    response.put("objectPath", fullPath);
                    response.put("parentPath", parentPath);
                    response.put("instanceName", instanceName);
                    response.put("blueprintName", model.name());
                    response.put("blueprintId", model.id());
                    response.put("objectType", instance.type().name());
                    response.put("templateId", model.name());
                    response.put("variableCount", instance.variables().size());
                    response.put("attachmentId", result.attachment().id());
                    if (!result.warnings().isEmpty()) {
                        response.put("warnings", result.warnings());
                    }
                    if (instance.type() == ObjectType.DEVICE) {
                        response.put("nextSteps", "configure_driver + driver_control start, then list_variables");
                    }
                    return response;
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool ensureAbsoluteInstanceTool(
            BlueprintRegistry blueprintRegistry,
            BlueprintApplicationService blueprintApplicationService,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "ensure_absolute_instance";
            }

            @Override
            public String description() {
                return "Ensure the singleton instance for an ABSOLUTE model exists under root.platform.instances.*. "
                        + "Args: blueprintName or blueprintId. Idempotent — returns existing path if already present.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                Optional<BlueprintDefinition> modelOpt = resolveBlueprint(blueprintRegistry, arguments);
                if (modelOpt.isEmpty()) {
                    return Map.of("status", "ERROR", "error", "blueprintName or blueprintId is required");
                }
                BlueprintDefinition model = modelOpt.get();
                if (model.type() != BlueprintType.ABSOLUTE) {
                    return Map.of(
                            "status", "ERROR",
                            "error", model.name() + " is " + model.type()
                                    + ". ensure_absolute_instance requires ABSOLUTE model."
                    );
                }
                String instancePath = BlueprintEngine.absoluteInstancePath(model);
                var auth = context.authentication();
                int lastDot = instancePath.lastIndexOf('.');
                String parentPath = lastDot > 0 ? instancePath.substring(0, lastDot) : instancePath;
                if (!tenantScopeService.isPathVisible(parentPath, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + parentPath);
                }
                objectAccessService.requireWrite(parentPath, auth);
                try {
                    PlatformObject instance = blueprintApplicationService.ensureAbsoluteInstanceWithRules(model.id());
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("status", "OK");
                    response.put("path", instance.path());
                    response.put("objectPath", instance.path());
                    response.put("blueprintName", model.name());
                    response.put("blueprintId", model.id());
                    response.put("objectType", instance.type().name());
                    response.put("variableCount", instance.variables().size());
                    response.put("nextSteps", "create_binding_rule / read(path/var) on hub variables, then list_variables");
                    return response;
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static Optional<BlueprintDefinition> resolveBlueprint(
            BlueprintRegistry blueprintRegistry,
            Map<String, Object> arguments
    ) {
        String modelId = stringArg(arguments, "blueprintId");
        if (!modelId.isBlank()) {
            return blueprintRegistry.findById(modelId).or(() -> blueprintRegistry.findByName(modelId));
        }
        String modelName = stringArg(arguments, "blueprintName");
        if (!modelName.isBlank()) {
            return blueprintRegistry.findByName(modelName).or(() -> blueprintRegistry.findById(modelName));
        }
        String model = stringArg(arguments, "model");
        if (!model.isBlank()) {
            return blueprintRegistry.findByName(model).or(() -> blueprintRegistry.findById(model));
        }
        String templateId = stringArg(arguments, "templateId");
        if (!templateId.isBlank()) {
            return blueprintRegistry.findByName(templateId).or(() -> blueprintRegistry.findById(templateId));
        }
        return Optional.empty();
    }

    private static Map<String, Object> blueprintSummary(BlueprintDefinition model) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("blueprintName", model.name());
        row.put("blueprintId", model.id());
        row.put("blueprintType", model.type().name());
        row.put("description", model.description());
        row.put("targetObjectType", model.targetObjectType().name());
        row.put("variableCount", model.variables().size());
        row.put("eventCount", model.events().size());
        row.put("functionCount", model.functions().size());
        if (model.type() == BlueprintType.ABSOLUTE) {
            row.put("absoluteInstancePath", BlueprintEngine.absoluteInstancePath(model));
        }
        String cel = model.suitabilityExpression();
        if (cel != null && !cel.isBlank()) {
            row.put("suitabilityExpression", cel.length() > 120 ? cel.substring(0, 119) + "…" : cel);
        }
        return row;
    }

    private static Map<String, Object> blueprintDetail(BlueprintDefinition model) {
        Map<String, Object> detail = blueprintSummary(model);
        detail.put("variables", model.variables().stream().map(v -> v.name()).toList());
        detail.put("events", model.events().stream().map(e -> e.name()).toList());
        detail.put("functions", model.functions().stream().map(f -> f.name()).toList());
        return detail;
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }
}
