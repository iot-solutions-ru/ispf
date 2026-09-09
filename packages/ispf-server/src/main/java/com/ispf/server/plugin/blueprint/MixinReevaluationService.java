package com.ispf.server.plugin.blueprint;

import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.blueprint.BlueprintApplyResult;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintDetachResult;
import com.ispf.plugin.blueprint.BlueprintEngine;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.plugin.blueprint.MixinReevaluationTrigger;
import com.ispf.server.object.ObjectManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates watch MIXINs for attach/detach (ADR-0058).
 */
@Service
public class MixinReevaluationService {

    private static final Logger log = LoggerFactory.getLogger(MixinReevaluationService.class);

    private final WatchMixinIndex watchMixinIndex;
    private final BlueprintRegistry blueprintRegistry;
    private final BlueprintEngine blueprintEngine;
    private final BlueprintApplicationService blueprintApplicationService;
    private final ObjectManager objectManager;

    public MixinReevaluationService(
            WatchMixinIndex watchMixinIndex,
            BlueprintRegistry blueprintRegistry,
            BlueprintEngine blueprintEngine,
            BlueprintApplicationService blueprintApplicationService,
            ObjectManager objectManager
    ) {
        this.watchMixinIndex = watchMixinIndex;
        this.blueprintRegistry = blueprintRegistry;
        this.blueprintEngine = blueprintEngine;
        this.blueprintApplicationService = blueprintApplicationService;
        this.objectManager = objectManager;
    }

    public record ReevaluationOutcome(
            String blueprintId,
            String objectPath,
            String action,
            BlueprintApplyResult applyResult,
            BlueprintDetachResult detachResult
    ) {
    }

    @Transactional
    public List<ReevaluationOutcome> reevaluateObject(String objectPath) {
        PlatformObject target = objectManager.require(objectPath);
        List<ReevaluationOutcome> outcomes = new ArrayList<>();
        for (BlueprintDefinition model : watchMixinIndex.allWatchers()) {
            reevaluateOne(model, target).ifPresent(outcomes::add);
        }
        return outcomes;
    }

    @Transactional
    public List<ReevaluationOutcome> reevaluateAllFor(String blueprintId) {
        BlueprintDefinition model = blueprintRegistry.requireById(blueprintId);
        if (!model.reevaluation().enabled()) {
            throw new IllegalArgumentException("Blueprint does not enable reevaluation: " + model.name());
        }
        List<ReevaluationOutcome> outcomes = new ArrayList<>();
        for (PlatformObject node : objectManager.tree().all()) {
            if (!blueprintEngine.isObjectTypeMatch(model, node)) {
                continue;
            }
            if (com.ispf.plugin.blueprint.BlueprintCatalogRoots.isCatalogPath(node.path())) {
                continue;
            }
            reevaluateOne(model, node).ifPresent(outcomes::add);
        }
        return outcomes;
    }

    @Transactional
    public List<ReevaluationOutcome> onServerReady() {
        List<ReevaluationOutcome> outcomes = new ArrayList<>();
        for (BlueprintDefinition model : watchMixinIndex.watchers(MixinReevaluationTrigger.SERVER_READY)) {
            for (PlatformObject node : objectManager.tree().all()) {
                if (!blueprintEngine.isObjectTypeMatch(model, node)) {
                    continue;
                }
                if (com.ispf.plugin.blueprint.BlueprintCatalogRoots.isCatalogPath(node.path())) {
                    continue;
                }
                reevaluateOne(model, node).ifPresent(outcomes::add);
            }
        }
        if (!outcomes.isEmpty()) {
            log.info("Mixin SERVER_READY reevaluation: {} attach/detach action(s)", outcomes.size());
        }
        return outcomes;
    }

    @Transactional
    public List<ReevaluationOutcome> onObjectCreated(String objectPath) {
        PlatformObject target = objectManager.require(objectPath);
        List<ReevaluationOutcome> outcomes = new ArrayList<>();
        for (BlueprintDefinition model : watchMixinIndex.watchers(MixinReevaluationTrigger.OBJECT_CREATED)) {
            reevaluateOne(model, target).ifPresent(outcomes::add);
        }
        return outcomes;
    }

    private java.util.Optional<ReevaluationOutcome> reevaluateOne(BlueprintDefinition model, PlatformObject target) {
        if (!blueprintEngine.isObjectTypeMatch(model, target)) {
            return java.util.Optional.empty();
        }
        boolean suitable = blueprintEngine.isSuitable(model, target);
        boolean applied = target.appliedBlueprintIds().contains(model.id());
        if (suitable && !applied) {
            BlueprintApplyResult apply = blueprintApplicationService.applyBlueprintWithRules(
                    model, target.path(), model.parameters()
            );
            return java.util.Optional.of(new ReevaluationOutcome(
                    model.id(), target.path(), "attach", apply, null
            ));
        }
        if (!suitable && applied) {
            BlueprintDetachResult detach = blueprintApplicationService.detachBlueprintWithRules(
                    model.id(), target.path()
            );
            if (!detach.skippedNames().isEmpty()) {
                log.warn(
                        "Mixin detach skipped owned-by-other names on {}: {}",
                        target.path(),
                        detach.skippedNames()
                );
            }
            return java.util.Optional.of(new ReevaluationOutcome(
                    model.id(), target.path(), "detach", null, detach
            ));
        }
        return java.util.Optional.empty();
    }
}
