package com.ispf.server.ai.agent;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.model.ModelApplyResult;
import com.ispf.plugin.model.ModelDefinition;
import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.plugin.model.ModelType;
import com.ispf.plugin.model.SystemIntrinsicModels;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.model.ModelApplicationService;
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
final class AgentModelTools {

    private AgentModelTools() {
    }

    static List<PlatformAgentTool> all(
            ModelRegistry modelRegistry,
            ModelApplicationService modelApplicationService,
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return List.of(
                listRelativeModelsTool(modelRegistry),
                listInstanceTypesTool(modelRegistry),
                listAbsoluteModelsTool(modelRegistry),
                getObjectModelTool(modelRegistry),
                applyRelativeModelTool(
                        modelRegistry,
                        modelApplicationService,
                        objectManager,
                        objectAccessService,
                        tenantScopeService
                ),
                instantiateInstanceTypeTool(
                        modelRegistry,
                        modelApplicationService,
                        objectManager,
                        objectAccessService,
                        tenantScopeService
                ),
                ensureAbsoluteInstanceTool(
                        modelRegistry,
                        modelApplicationService,
                        objectAccessService,
                        tenantScopeService
                )
        );
    }

    private static PlatformAgentTool listRelativeModelsTool(ModelRegistry modelRegistry) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "list_relative_models";
            }

            @Override
            public String description() {
                return "List RELATIVE model blueprints (mixins) under root.platform.relative-models. "
                        + "They add variables, events, functions to existing objects via apply_relative_model. "
                        + "Optional query filter and targetObjectType (DEVICE, CUSTOM, …).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                return listModelsByType(modelRegistry, ModelType.RELATIVE, arguments, true);
            }
        };
    }

    private static PlatformAgentTool listInstanceTypesTool(ModelRegistry modelRegistry) {
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
                Map<String, Object> result = listModelsByType(modelRegistry, ModelType.INSTANCE, arguments, false);
                result.put(
                        "hint",
                        "Create with instantiate_instance_type parentPath=... instanceName=... modelName=<name>"
                );
                return result;
            }
        };
    }

    private static PlatformAgentTool listAbsoluteModelsTool(ModelRegistry modelRegistry) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "list_absolute_models";
            }

            @Override
            public String description() {
                return "List ABSOLUTE model blueprints (singleton hubs) under root.platform.absolute-models. "
                        + "Each has one live instance under root.platform.instances.* — use ensure_absolute_instance.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                Map<String, Object> result = listModelsByType(modelRegistry, ModelType.ABSOLUTE, arguments, false);
                result.put(
                        "hint",
                        "Use ensure_absolute_instance modelName=<name> — never instantiate twice (409 if exists)"
                );
                return result;
            }
        };
    }

    private static Map<String, Object> listModelsByType(
            ModelRegistry modelRegistry,
            ModelType type,
            Map<String, Object> arguments,
            boolean excludeIntrinsic
    ) {
        String query = stringArg(arguments, "query").toLowerCase(Locale.ROOT);
        String targetFilter = stringArg(arguments, "targetObjectType").toUpperCase(Locale.ROOT);
        if (targetFilter.isBlank()) {
            targetFilter = stringArg(arguments, "platformType").toUpperCase(Locale.ROOT);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ModelDefinition model : modelRegistry.all()) {
            if (model.type() != type) {
                continue;
            }
            if (excludeIntrinsic && SystemIntrinsicModels.isIntrinsic(model)) {
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
            rows.add(modelSummary(model));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "OK");
        result.put("modelType", type.name());
        result.put("count", rows.size());
        result.put("models", rows);
        return result;
    }

    private static PlatformAgentTool getObjectModelTool(ModelRegistry modelRegistry) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "get_object_model";
            }

            @Override
            public String description() {
                return "Schema of a model blueprint: variables, events, functions, modelType, targetObjectType. "
                        + "Args: modelName or modelId (e.g. virtual-lab-v1).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                Optional<ModelDefinition> model = resolveModel(modelRegistry, arguments);
                if (model.isEmpty()) {
                    return Map.of(
                            "status", "ERROR",
                            "error", "modelName or modelId required; use list_relative_models or list_object_models"
                    );
                }
                return Map.of("status", "OK", "model", modelDetail(model.get()));
            }
        };
    }

    private static PlatformAgentTool applyRelativeModelTool(
            ModelRegistry modelRegistry,
            ModelApplicationService modelApplicationService,
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "apply_relative_model";
            }

            @Override
            public String description() {
                return "Attach a RELATIVE model mixin to an existing object — merges variables, events, functions, "
                        + "binding rules without changing object path. "
                        + "Args: objectPath (required), modelName or modelId (e.g. virtual-lab-v1). "
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
                Optional<ModelDefinition> modelOpt = resolveModel(modelRegistry, arguments);
                if (modelOpt.isEmpty()) {
                    return Map.of("status", "ERROR", "error", "modelName or modelId is required");
                }
                ModelDefinition model = modelOpt.get();
                if (model.type() != ModelType.RELATIVE) {
                    return Map.of(
                            "status", "ERROR",
                            "error", model.name() + " is " + model.type() + ", not RELATIVE. "
                                    + "Use instantiate_instance_type for INSTANCE or ensure_absolute_instance for ABSOLUTE."
                    );
                }
                if (SystemIntrinsicModels.isIntrinsic(model)) {
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
                    ModelApplyResult applyResult = modelApplicationService.applyModelWithRules(
                            model.id(),
                            objectPath
                    );
                    PlatformObject updated = objectManager.require(objectPath);
                    int variablesAfter = updated.variables().size();
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("status", "OK");
                    result.put("objectPath", objectPath);
                    result.put("modelName", model.name());
                    result.put("modelId", model.id());
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
            ModelRegistry modelRegistry,
            ModelApplicationService modelApplicationService,
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
                        + "Args: parentPath (required), instanceName (required), modelName or modelId. "
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
                Optional<ModelDefinition> modelOpt = resolveModel(modelRegistry, arguments);
                if (modelOpt.isEmpty()) {
                    return Map.of("status", "ERROR", "error", "modelName or modelId is required");
                }
                ModelDefinition model = modelOpt.get();
                if (model.type() != ModelType.INSTANCE) {
                    return Map.of(
                            "status", "ERROR",
                            "error", model.name() + " is " + model.type()
                                    + ". Use apply_relative_model for RELATIVE or ensure_absolute_instance for ABSOLUTE."
                    );
                }
                var auth = context.authentication();
                if (!tenantScopeService.isPathVisible(parentPath, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + parentPath);
                }
                objectAccessService.requireWrite(parentPath, auth);
                try {
                    ModelApplyResult result = modelApplicationService.instantiateWithRules(
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
                    response.put("modelName", model.name());
                    response.put("modelId", model.id());
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
            ModelRegistry modelRegistry,
            ModelApplicationService modelApplicationService,
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
                        + "Args: modelName or modelId. Idempotent — returns existing path if already present.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                Optional<ModelDefinition> modelOpt = resolveModel(modelRegistry, arguments);
                if (modelOpt.isEmpty()) {
                    return Map.of("status", "ERROR", "error", "modelName or modelId is required");
                }
                ModelDefinition model = modelOpt.get();
                if (model.type() != ModelType.ABSOLUTE) {
                    return Map.of(
                            "status", "ERROR",
                            "error", model.name() + " is " + model.type()
                                    + ". ensure_absolute_instance requires ABSOLUTE model."
                    );
                }
                String instancePath = ModelEngine.absoluteInstancePath(model);
                var auth = context.authentication();
                int lastDot = instancePath.lastIndexOf('.');
                String parentPath = lastDot > 0 ? instancePath.substring(0, lastDot) : instancePath;
                if (!tenantScopeService.isPathVisible(parentPath, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + parentPath);
                }
                objectAccessService.requireWrite(parentPath, auth);
                try {
                    PlatformObject instance = modelApplicationService.ensureAbsoluteInstanceWithRules(model.id());
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("status", "OK");
                    response.put("path", instance.path());
                    response.put("objectPath", instance.path());
                    response.put("modelName", model.name());
                    response.put("modelId", model.id());
                    response.put("objectType", instance.type().name());
                    response.put("variableCount", instance.variables().size());
                    response.put("nextSteps", "create_binding_rule / refAt on hub variables, then list_variables");
                    return response;
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static Optional<ModelDefinition> resolveModel(
            ModelRegistry modelRegistry,
            Map<String, Object> arguments
    ) {
        String modelId = stringArg(arguments, "modelId");
        if (!modelId.isBlank()) {
            return modelRegistry.findById(modelId).or(() -> modelRegistry.findByName(modelId));
        }
        String modelName = stringArg(arguments, "modelName");
        if (!modelName.isBlank()) {
            return modelRegistry.findByName(modelName).or(() -> modelRegistry.findById(modelName));
        }
        String model = stringArg(arguments, "model");
        if (!model.isBlank()) {
            return modelRegistry.findByName(model).or(() -> modelRegistry.findById(model));
        }
        String templateId = stringArg(arguments, "templateId");
        if (!templateId.isBlank()) {
            return modelRegistry.findByName(templateId).or(() -> modelRegistry.findById(templateId));
        }
        return Optional.empty();
    }

    private static Map<String, Object> modelSummary(ModelDefinition model) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("modelName", model.name());
        row.put("modelId", model.id());
        row.put("modelType", model.type().name());
        row.put("description", model.description());
        row.put("targetObjectType", model.targetObjectType().name());
        row.put("variableCount", model.variables().size());
        row.put("eventCount", model.events().size());
        row.put("functionCount", model.functions().size());
        if (model.type() == ModelType.ABSOLUTE) {
            row.put("absoluteInstancePath", ModelEngine.absoluteInstancePath(model));
        }
        String cel = model.suitabilityExpression();
        if (cel != null && !cel.isBlank()) {
            row.put("suitabilityExpression", cel.length() > 120 ? cel.substring(0, 119) + "…" : cel);
        }
        return row;
    }

    private static Map<String, Object> modelDetail(ModelDefinition model) {
        Map<String, Object> detail = modelSummary(model);
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
