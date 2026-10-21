package com.ispf.server.object;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.blueprint.BlueprintCatalogMetadata;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.plugin.blueprint.BlueprintVariableDefinition;
import com.ispf.plugin.blueprint.SystemIntrinsicBlueprints;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Variable names the platform writes as the schema of a catalog object.
 * Operator-added names on the same object are not included.
 */
public final class CatalogSchemaVariables {

    private CatalogSchemaVariables() {
    }

    public static Set<String> names(PlatformObject node, BlueprintRegistry registry) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (node == null) {
            return Set.of();
        }
        if (node.type() == ObjectType.BLUEPRINT) {
            names.add(BlueprintCatalogMetadata.BLUEPRINT_TYPE);
            names.add(BlueprintCatalogMetadata.SUITABILITY_EXPRESSION);
        }
        if (registry == null) {
            return Set.copyOf(names);
        }
        String blueprintName = SystemIntrinsicBlueprints.blueprintNameForObjectType(node.type());
        if (blueprintName == null) {
            return Set.copyOf(names);
        }
        registry.findByName(blueprintName).ifPresent(model -> {
            for (BlueprintVariableDefinition variable : model.variables()) {
                names.add(variable.name());
            }
        });
        return Set.copyOf(names);
    }

    public static boolean isIntrinsicSchemaVariable(
            PlatformObject node,
            String name,
            BlueprintRegistry registry
    ) {
        return name != null && names(node, registry).contains(name);
    }
}
