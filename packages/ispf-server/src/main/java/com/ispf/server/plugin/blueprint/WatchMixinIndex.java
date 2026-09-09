package com.ispf.server.plugin.blueprint;

import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.plugin.blueprint.BlueprintType;
import com.ispf.plugin.blueprint.MixinReevaluationTrigger;
import com.ispf.plugin.blueprint.SystemIntrinsicBlueprints;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Index of MIXINs with opt-in reevaluation (ADR-0058).
 */
@Component
public class WatchMixinIndex {

    private final BlueprintRegistry blueprintRegistry;
    private final Map<MixinReevaluationTrigger, List<String>> byTrigger = new ConcurrentHashMap<>();

    public WatchMixinIndex(BlueprintRegistry blueprintRegistry) {
        this.blueprintRegistry = blueprintRegistry;
        rebuild();
    }

    public synchronized void rebuild() {
        Map<MixinReevaluationTrigger, List<String>> next = new EnumMap<>(MixinReevaluationTrigger.class);
        for (MixinReevaluationTrigger trigger : MixinReevaluationTrigger.values()) {
            next.put(trigger, new ArrayList<>());
        }
        for (BlueprintDefinition model : blueprintRegistry.all()) {
            if (model.type() != BlueprintType.MIXIN) {
                continue;
            }
            if (SystemIntrinsicBlueprints.isIntrinsic(model)) {
                continue;
            }
            if (!model.reevaluation().enabled()) {
                continue;
            }
            for (MixinReevaluationTrigger trigger : model.reevaluation().triggers()) {
                next.computeIfAbsent(trigger, t -> new ArrayList<>()).add(model.id());
            }
        }
        byTrigger.clear();
        byTrigger.putAll(next);
    }

    public List<BlueprintDefinition> watchers(MixinReevaluationTrigger trigger) {
        List<String> ids = byTrigger.getOrDefault(trigger, List.of());
        List<BlueprintDefinition> out = new ArrayList<>();
        for (String id : ids) {
            blueprintRegistry.findById(id).ifPresent(out::add);
        }
        return out;
    }

    public List<BlueprintDefinition> allWatchers() {
        return blueprintRegistry.all().stream()
                .filter(m -> m.type() == BlueprintType.MIXIN)
                .filter(m -> !SystemIntrinsicBlueprints.isIntrinsic(m))
                .filter(m -> m.reevaluation().enabled())
                .toList();
    }
}
