package com.ispf.plugin.model;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Full model definition — blueprint for object structure and binding rules.
 */
public record ModelDefinition(
        String id,
        String name,
        String description,
        ModelType type,
        ObjectType targetObjectType,
        String suitabilityExpression,
        List<ModelVariableDefinition> variables,
        List<EventDescriptor> events,
        List<FunctionDescriptor> functions,
        List<ModelBindingRule> bindingRules,
        Map<String, String> parameters,
        Instant createdAt,
        Instant updatedAt
) {
    public ModelDefinition {
        if (description == null) {
            description = "";
        }
        if (suitabilityExpression == null) {
            suitabilityExpression = "";
        }
        if (parameters == null) {
            parameters = Map.of();
        }
        variables = List.copyOf(variables != null ? variables : List.of());
        events = List.copyOf(events != null ? events : List.of());
        functions = List.copyOf(functions != null ? functions : List.of());
        bindingRules = List.copyOf(bindingRules != null ? bindingRules : List.of());
    }

    /** @deprecated use {@link #bindingRules()} */
    @Deprecated
    public List<ModelBindingRule> bindings() {
        return bindingRules;
    }

    public String objectPath(String modelsRoot) {
        return modelsRoot + "." + name;
    }

    public String catalogRoot() {
        return ModelCatalogRoots.catalogRoot(type);
    }

    public String catalogObjectPath() {
        return catalogRoot() + "." + name;
    }

    /** Semantic version string stored in {@link #parameters()} under {@code modelVersion}. */
    public String modelVersion() {
        return parameters.getOrDefault("modelVersion", "1");
    }

    public boolean systemIntrinsic() {
        return "true".equalsIgnoreCase(parameters.getOrDefault(SystemIntrinsicModels.PARAM_SYSTEM_INTRINSIC, "false"));
    }

    public ModelDefinition withSystemIntrinsicFlag() {
        if (systemIntrinsic()) {
            return this;
        }
        Map<String, String> params = new LinkedHashMap<>(parameters);
        params.put(SystemIntrinsicModels.PARAM_SYSTEM_INTRINSIC, "true");
        return new ModelDefinition(
                id,
                name,
                description,
                type,
                targetObjectType,
                suitabilityExpression,
                variables,
                events,
                functions,
                bindingRules,
                params,
                createdAt,
                Instant.now()
        );
    }
}
