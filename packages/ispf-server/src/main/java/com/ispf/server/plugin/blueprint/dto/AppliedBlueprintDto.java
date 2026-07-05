package com.ispf.server.plugin.blueprint.dto;

import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.plugin.blueprint.BlueprintType;
import com.ispf.plugin.blueprint.SystemIntrinsicBlueprints;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record AppliedBlueprintDto(
        String id,
        String name,
        BlueprintType type,
        boolean primary
) {
    public static AppliedBlueprintDto of(BlueprintDefinition model, boolean primary) {
        return new AppliedBlueprintDto(model.id(), model.name(), model.type(), primary);
    }

    public static List<AppliedBlueprintDto> resolve(PlatformObject node, BlueprintRegistry registry) {
        String primary = node.templateId().orElse(null);
        Set<String> seen = new LinkedHashSet<>();
        List<AppliedBlueprintDto> result = new ArrayList<>();
        for (String modelId : node.appliedBlueprintIds()) {
            addResolved(registry, modelId, primary, seen, result);
        }
        if (primary != null && !primary.isBlank() && !SystemIntrinsicBlueprints.isIntrinsicName(primary)) {
            addResolved(registry, primary, primary, seen, result);
        }
        return result;
    }

    private static void addResolved(
            BlueprintRegistry registry,
            String modelRef,
            String primary,
            Set<String> seen,
            List<AppliedBlueprintDto> result
    ) {
        if (modelRef == null || modelRef.isBlank() || !seen.add(modelRef)) {
            return;
        }
        Optional<BlueprintDefinition> model = registry.findById(modelRef);
        if (model.isEmpty()) {
            model = registry.findByName(modelRef);
        }
        model.ifPresent(definition -> {
            if (SystemIntrinsicBlueprints.isIntrinsic(definition)) {
                return;
            }
            result.add(AppliedBlueprintDto.of(definition, modelRef.equals(primary) || definition.id().equals(primary)));
        });
    }
}
