package com.ispf.server.plugin.blueprint;

import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.blueprint.BlueprintApplyResult;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintEngine;
import com.ispf.plugin.blueprint.BlueprintException;
import com.ispf.plugin.blueprint.BlueprintMergeWarning;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Unified blueprint apply path: structure merge, binding rules, persistence of appliedBlueprintIds.
 */
@Service
public class BlueprintApplicationService {

    private final BlueprintEngine blueprintEngine;
    private final BlueprintRegistry blueprintRegistry;
    private final BlueprintBindingRulesMerger bindingRulesMerger;
    private final ObjectManager objectManager;

    public BlueprintApplicationService(
            BlueprintEngine blueprintEngine,
            BlueprintRegistry blueprintRegistry,
            BlueprintBindingRulesMerger bindingRulesMerger,
            ObjectManager objectManager
    ) {
        this.blueprintEngine = blueprintEngine;
        this.blueprintRegistry = blueprintRegistry;
        this.bindingRulesMerger = bindingRulesMerger;
        this.objectManager = objectManager;
    }

    @Transactional
    public BlueprintApplyResult applyBlueprintWithRules(String blueprintId, String objectPath) {
        return applyBlueprintWithRules(blueprintRegistry.requireById(blueprintId), objectPath, Map.of());
    }

    @Transactional
    public BlueprintApplyResult applyBlueprintWithRules(BlueprintDefinition model, String objectPath, Map<String, String> parameters) {
        try {
            if (com.ispf.plugin.blueprint.SystemIntrinsicBlueprints.isIntrinsic(model)) {
                BlueprintApplyResult result = blueprintEngine.applyIntrinsicStructure(model, objectPath);
                bindingRulesMerger.mergeBlueprintRules(objectPath, model, parameters);
                objectManager.persistNodeTree(objectPath);
                return result;
            }
            BlueprintApplyResult result = blueprintEngine.applyBlueprint(model.id(), objectPath);
            bindingRulesMerger.mergeBlueprintRules(objectPath, model, parameters);
            objectManager.persistNodeTree(objectPath);
            return result;
        } catch (BlueprintException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Transactional
    public BlueprintApplyResult instantiateWithRules(
            String blueprintId,
            String parentPath,
            String instanceName,
            Map<String, String> parameters
    ) {
        try {
            BlueprintApplyResult result = blueprintEngine.instantiateBlueprint(blueprintId, parentPath, instanceName, parameters);
            // Use in-memory tree only: object is not persisted yet; cluster require() evicts RAM-only nodes.
            String instancePath = objectManager.tree().resolveChildPath(parentPath, instanceName);
            PlatformObject instance = objectManager.tree().require(instancePath);
            BlueprintDefinition model = blueprintRegistry.requireById(blueprintId);
            objectManager.persistNodeTree(instance.path());
            bindingRulesMerger.mergeBlueprintRules(
                    instance.path(), model, parameters != null ? parameters : Map.of());
            List<BlueprintApplyResult> relativeResults = blueprintEngine.applyRelativeBlueprints(instance.path());
            for (BlueprintApplyResult relative : relativeResults) {
                blueprintRegistry.findById(relative.attachment().blueprintId()).ifPresent(relativeModel ->
                        bindingRulesMerger.mergeBlueprintRules(
                                instance.path(),
                                relativeModel,
                                relativeModel.parameters()
                        )
                );
            }
            objectManager.persistNodeTree(instance.path());
            return aggregateResults(result, relativeResults);
        } catch (BlueprintException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Transactional
    public List<BlueprintApplyResult> applyRelativeBlueprintsWithRules(String objectPath) {
        List<BlueprintApplyResult> results = blueprintEngine.applyRelativeBlueprints(objectPath);
        for (BlueprintApplyResult result : results) {
            blueprintRegistry.findById(result.attachment().blueprintId()).ifPresent(model ->
                    bindingRulesMerger.mergeBlueprintRules(objectPath, model, model.parameters())
            );
        }
        if (!results.isEmpty()) {
            objectManager.persistNodeTree(objectPath);
        }
        return results;
    }

    public void restoreAttachments() {
        blueprintEngine.restoreAttachmentsFromObjects();
    }

    @Transactional
    public PlatformObject ensureAbsoluteInstanceWithRules(String blueprintId) {
        try {
            BlueprintDefinition model = blueprintRegistry.requireById(blueprintId);
            PlatformObject instance = blueprintEngine.ensureAbsoluteInstance(model);
            bindingRulesMerger.mergeBlueprintRules(instance.path(), model, model.parameters());
            objectManager.persistNodeTree(instance.path());
            return objectManager.require(instance.path());
        } catch (BlueprintException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private static BlueprintApplyResult aggregateResults(BlueprintApplyResult primary, List<BlueprintApplyResult> additional) {
        List<BlueprintMergeWarning> warnings = new ArrayList<>(primary.warnings());
        for (BlueprintApplyResult result : additional) {
            warnings.addAll(result.warnings());
        }
        return new BlueprintApplyResult(primary.attachment(), warnings);
    }
}
