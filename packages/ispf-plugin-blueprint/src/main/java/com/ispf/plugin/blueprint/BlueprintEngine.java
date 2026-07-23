package com.ispf.plugin.blueprint;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.Variable;
import com.ispf.core.binding.BindingRulesConstants;
import com.ispf.expression.ExpressionEngine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Core engine for blueprint lifecycle — create, apply, instantiate, template export.
 */
public class BlueprintEngine {

    private final BlueprintRegistry registry;
    private final ObjectTree objectTree;
    private final ExpressionEngine expressionEngine;
    private final List<BlueprintAttachment> attachments = new CopyOnWriteArrayList<>();

    public BlueprintEngine(
            BlueprintRegistry registry,
            ObjectTree objectTree,
            ExpressionEngine expressionEngine
    ) {
        this.registry = registry;
        this.objectTree = objectTree;
        this.expressionEngine = expressionEngine;
    }

    public BlueprintDefinition createBlueprint(BlueprintDefinition model) {
        validateBlueprintType(model);
        ensureCatalogContainers();
        BlueprintDefinition stored = registry.register(model);
        registerBlueprintObject(stored);
        if (stored.type() == BlueprintType.SINGLETON) {
            ensureSingletonInstance(stored);
        }
        return stored;
    }

    public BlueprintDefinition updateBlueprint(BlueprintDefinition model) {
        validateBlueprintType(model);
        ensureCatalogContainers();
        BlueprintDefinition stored = registry.update(model);
        registerBlueprintObject(stored);
        if (stored.type() == BlueprintType.SINGLETON) {
            syncSingletonInstance(stored);
        }
        return stored;
    }

    public void deleteBlueprint(String blueprintId) {
        BlueprintDefinition model = registry.requireById(blueprintId);
        registry.delete(blueprintId);
        objectTree.findByPath(model.catalogObjectPath()).ifPresent(node ->
                attachments.removeIf(a -> a.blueprintId().equals(blueprintId))
        );
    }

    /**
     * Merges blueprint variables, events, functions into an existing object (MIXIN / manual apply).
     * Binding rules are merged separately via {@code BlueprintBindingRulesMerger} in ispf-server.
     */
    public BlueprintApplyResult applyBlueprint(String blueprintId, String targetPath) {
        BlueprintDefinition model = registry.requireById(blueprintId);
        if (SystemIntrinsicBlueprints.isIntrinsic(model)) {
            return applyIntrinsicStructure(model, targetPath);
        }
        PlatformObject target = objectTree.require(targetPath);
        assertSuitable(model, target);
        List<BlueprintMergeWarning> warnings = new ArrayList<>();
        mergeBlueprintChain(model, target, model.parameters(), warnings);
        target.addAppliedBlueprintId(model.id());
        BlueprintAttachment attachment = recordAttachment(model, targetPath);
        return new BlueprintApplyResult(attachment, warnings);
    }

    /**
     * Merges a system-intrinsic schema into an object without catalog attachment or {@code appliedBlueprintIds}.
     */
    public BlueprintApplyResult applyIntrinsicStructure(BlueprintDefinition model, String targetPath) {
        PlatformObject target = objectTree.require(targetPath);
        List<BlueprintMergeWarning> warnings = new ArrayList<>();
        mergeBlueprintChain(model, target, model.parameters(), warnings);
        return new BlueprintApplyResult(null, warnings);
    }

    public void applyIntrinsicStructureByName(String blueprintName, String targetPath) {
        registry.findByName(blueprintName).ifPresent(model -> applyIntrinsicStructure(model, targetPath));
    }

    public void removeIntrinsicCatalogNodes() {
        for (String name : SystemIntrinsicBlueprints.NAMES) {
            removeCatalogNodeIfPresent(BlueprintCatalogRoots.MIXIN + "." + name);
        }
    }

    private void removeCatalogNodeIfPresent(String path) {
        if (objectTree.findByPath(path).isPresent()) {
            objectTree.delete(path);
        }
    }

    /**
     * Creates a new child object from a blueprint (INSTANCE / SINGLETON semantics).
     */
    public BlueprintApplyResult instantiateBlueprint(
            String blueprintId,
            String parentPath,
            String instanceName,
            Map<String, String> parameters
    ) {
        BlueprintDefinition model = registry.requireById(blueprintId);
        if (model.type() == BlueprintType.MIXIN) {
            throw new BlueprintException("Mixin Blueprints cannot be instantiated as child objects. Use apply instead.");
        }
        if (model.type() == BlueprintType.SINGLETON) {
            throw new BlueprintException(
                    "Singleton Blueprints use a fixed singleton instance. Open the instance path instead of instantiate."
            );
        }

        String fullPath = objectTree.resolveChildPath(parentPath, instanceName);
        if (objectTree.findByPath(fullPath).isPresent()) {
            throw new BlueprintException("Object already exists: " + fullPath);
        }

        ObjectType objectType = model.targetObjectType() != null
                ? model.targetObjectType()
                : ObjectType.CUSTOM;

        PlatformObject instance = new PlatformObject(
                UUID.randomUUID().toString(),
                fullPath,
                objectType,
                instanceName,
                model.description(),
                model.id()
        );
        objectTree.register(instance);
        List<BlueprintMergeWarning> warnings = new ArrayList<>();
        mergeBlueprintChain(model, instance, parameters, warnings);
        instance.addAppliedBlueprintId(model.id());

        BlueprintAttachment attachment = recordAttachment(model, fullPath);
        return new BlueprintApplyResult(attachment, warnings);
    }

    public PlatformObject ensureSingletonInstance(BlueprintDefinition model) {
        if (model.type() != BlueprintType.SINGLETON) {
            throw new BlueprintException("ensureSingletonInstance requires SINGLETON blueprint");
        }
        String instancePath = singletonInstancePath(model);
        PlatformObject instance = objectTree.findByPath(instancePath).orElseGet(() -> {
            ensureParentForSingletonPath(instancePath);
            ObjectType objectType = model.targetObjectType() != null
                    ? model.targetObjectType()
                    : ObjectType.CUSTOM;
            PlatformObject created = new PlatformObject(
                    UUID.randomUUID().toString(),
                    instancePath,
                    objectType,
                    model.name(),
                    model.description(),
                    model.id()
            );
            objectTree.register(created);
            return created;
        });
        List<BlueprintMergeWarning> warnings = new ArrayList<>();
        mergeBlueprintChain(model, instance, model.parameters(), warnings);
        instance.addAppliedBlueprintId(model.id());
        recordAttachment(model, instancePath);
        return instance;
    }

    private void syncSingletonInstance(BlueprintDefinition model) {
        ensureSingletonInstance(model);
    }

    /**
     * Live singleton path: by default the catalog node itself
     * ({@code root.platform.singleton-blueprints.{name}}), which carries application logic.
     * Override with {@code parameters.singletonInstancePath} when needed.
     */
    public static String singletonInstancePath(BlueprintDefinition model) {
        String configured = model.parameters().get("singletonInstancePath");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return model.catalogObjectPath();
    }

    private void ensureParentForSingletonPath(String instancePath) {
        int lastDot = instancePath.lastIndexOf('.');
        if (lastDot <= 0) {
            throw new BlueprintException("Invalid singleton instance path: " + instancePath);
        }
        String parentPath = instancePath.substring(0, lastDot);
        if (parentPath.equals(BlueprintCatalogRoots.INSTANCES)
                || parentPath.startsWith(BlueprintCatalogRoots.INSTANCES + ".")) {
            ensureInstancesContainer();
        }
        if (objectTree.findByPath(parentPath).isEmpty()) {
            throw new BlueprintException("Singleton instance parent missing: " + parentPath);
        }
    }

    /**
     * Creates a blueprint definition by snapshotting an existing object (variables/events/functions only).
     */
    public BlueprintDefinition createFromObject(
            String sourcePath,
            String blueprintName,
            String description,
            BlueprintType type
    ) {
        PlatformObject source = objectTree.require(sourcePath);
        List<BlueprintVariableDefinition> variables = source.variables().values().stream()
                .filter(v -> !BindingRulesConstants.isReservedVariable(v.name()))
                .map(v -> BlueprintVariableDefinition.of(
                        v.name(),
                        "",
                        "default",
                        v.schema(),
                        v.readable(),
                        v.writable(),
                        v.value().orElse(null),
                        v.historyEnabled(),
                        v.historyRetentionDays().orElse(null)
                ))
                .toList();

        BlueprintDefinition model = new BlueprintDefinition(
                UUID.randomUUID().toString(),
                blueprintName,
                description != null ? description : "Created from " + sourcePath,
                type,
                source.type(),
                "",
                variables,
                new ArrayList<>(source.events().values()),
                new ArrayList<>(source.functions().values()),
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
        return createBlueprint(model);
    }

    /**
     * Applies Mixin Blueprints with a non-blank applicability (CEL) expression that evaluates to true.
     */
    public List<BlueprintApplyResult> applyMixinBlueprints(String targetPath) {
        PlatformObject target = objectTree.require(targetPath);
        List<BlueprintApplyResult> applied = new ArrayList<>();
        for (BlueprintDefinition model : registry.all()) {
            if (model.type() != BlueprintType.MIXIN) {
                continue;
            }
            if (SystemIntrinsicBlueprints.isIntrinsic(model)) {
                continue;
            }
            if (target.appliedBlueprintIds().contains(model.id())) {
                continue;
            }
            if (!isSuitableForAutoApply(model, target)) {
                continue;
            }
            applied.add(applyBlueprint(model.id(), targetPath));
        }
        return applied;
    }

    public void restoreAttachmentsFromObjects() {
        attachments.clear();
        for (PlatformObject node : objectTree.all()) {
            if (BlueprintCatalogRoots.isDefinitionPath(node.path())) {
                continue;
            }
            for (String modelId : node.appliedBlueprintIds()) {
                registry.findById(modelId).ifPresent(model ->
                        recordAttachment(model, node.path())
                );
            }
        }
    }

    public List<BlueprintAttachment> attachments() {
        return List.copyOf(attachments);
    }

    public List<BlueprintAttachment> attachmentsForObject(String objectPath) {
        return attachments.stream()
                .filter(a -> a.objectPath().equals(objectPath))
                .toList();
    }

    public void refreshBlueprintCatalogNodes() {
        ensureCatalogContainers();
        removeIntrinsicCatalogNodes();
        for (BlueprintDefinition model : registry.all()) {
            if (!SystemIntrinsicBlueprints.isIntrinsic(model)) {
                registerBlueprintObject(model);
            }
        }
    }

    public boolean isBlueprintCatalogPath(String path) {
        return BlueprintCatalogRoots.isCatalogPath(path);
    }

    private BlueprintAttachment recordAttachment(BlueprintDefinition model, String targetPath) {
        BlueprintAttachment attachment = new BlueprintAttachment(
                UUID.randomUUID().toString(),
                model.id(),
                model.name(),
                model.type(),
                targetPath,
                Instant.now()
        );
        attachments.removeIf(a -> a.blueprintId().equals(model.id()) && a.objectPath().equals(targetPath));
        attachments.add(attachment);
        return attachment;
    }

    public void ensureCatalogContainers() {
        ensureCatalogContainer(BlueprintCatalogRoots.MIXIN, "Mixin Blueprints", "Mixin blueprints applied to existing objects");
        ensureCatalogContainer(BlueprintCatalogRoots.INSTANCE, "Instance Types", "Blueprints for new object instances");
        ensureCatalogContainer(BlueprintCatalogRoots.SINGLETON, "Singleton Blueprints",
                "Singleton live hubs with application logic");
        ensureInstancesContainer();
    }

    private void ensureInstancesContainer() {
        if (objectTree.findByPath(BlueprintCatalogRoots.INSTANCES).isEmpty()) {
            objectTree.register(new PlatformObject(
                    UUID.randomUUID().toString(),
                    BlueprintCatalogRoots.INSTANCES,
                    ObjectType.CUSTOM,
                    "Instances",
                    "User-created object instances (devices, meters, …)",
                    null
            ));
        }
    }

    private void ensureCatalogContainer(String path, String displayName, String description) {
        if (objectTree.findByPath(path).isEmpty()) {
            String parentPath = path.substring(0, path.lastIndexOf('.'));
            if (objectTree.findByPath(parentPath).isEmpty()) {
                throw new BlueprintException("Catalog parent object missing: " + parentPath);
            }
            objectTree.register(new PlatformObject(
                    UUID.randomUUID().toString(),
                    path,
                    ObjectType.BLUEPRINT,
                    displayName,
                    description,
                    null
            ));
        }
    }

    private void registerBlueprintObject(BlueprintDefinition model) {
        if (SystemIntrinsicBlueprints.isIntrinsic(model)) {
            removeCatalogNodeIfPresent(model.catalogObjectPath());
            return;
        }
        ensureCatalogContainers();
        String path = model.catalogObjectPath();

        PlatformObject modelObject = objectTree.findByPath(path).orElseGet(() -> {
            PlatformObject node = new PlatformObject(
                    UUID.randomUUID().toString(),
                    path,
                    ObjectType.BLUEPRINT,
                    model.name(),
                    model.description(),
                    model.id()
            );
            objectTree.register(node);
            return node;
        });

        modelObject.updateInfo(model.name(), model.description());

        modelObject.addVariable(new Variable(
                "blueprintType",
                com.ispf.core.model.DataSchema.builder("blueprintType").field("value", com.ispf.core.model.FieldType.STRING).build(),
                true,
                false,
                com.ispf.core.model.DataRecord.single(
                        com.ispf.core.model.DataSchema.builder("blueprintType").field("value", com.ispf.core.model.FieldType.STRING).build(),
                        Map.of("value", model.type().name())
                )
        ));
        modelObject.addVariable(new Variable(
                "suitabilityExpression",
                com.ispf.core.model.DataSchema.builder("expr").field("value", com.ispf.core.model.FieldType.STRING).build(),
                true,
                true,
                com.ispf.core.model.DataRecord.single(
                        com.ispf.core.model.DataSchema.builder("expr").field("value", com.ispf.core.model.FieldType.STRING).build(),
                        Map.of("value", model.suitabilityExpression())
                )
        ));
    }

    private void mergeBlueprintChain(
            BlueprintDefinition model,
            PlatformObject target,
            Map<String, String> parameters,
            List<BlueprintMergeWarning> warnings
    ) {
        String parentRef = model.parameters().get("extendsBlueprintId");
        if (parentRef != null && !parentRef.isBlank()) {
            BlueprintDefinition parent = registry.findById(parentRef)
                    .or(() -> registry.findByName(parentRef))
                    .orElseThrow(() -> new BlueprintException("Parent model not found: " + parentRef));
            mergeBlueprintChain(parent, target, parameters, warnings);
        }
        mergeModelIntoObject(model, target, parameters, warnings);
    }

    private void mergeModelIntoObject(
            BlueprintDefinition model,
            PlatformObject target,
            Map<String, String> parameters,
            List<BlueprintMergeWarning> warnings
    ) {
        String previousBlueprintId = target.lastAppliedBlueprintId();
        for (BlueprintVariableDefinition varDef : model.variables()) {
            if (target.getVariable(varDef.name()).isPresent()) {
                warnings.add(new BlueprintMergeWarning(
                        BlueprintMergeWarning.KIND_VARIABLE,
                        varDef.name(),
                        previousBlueprintId,
                        model.id()
                ));
            }
            Variable variable = new Variable(
                    varDef.name(),
                    varDef.schema(),
                    varDef.readable(),
                    varDef.writable(),
                    varDef.defaultValue(),
                    varDef.historyEnabled(),
                    varDef.historyRetentionDays()
            );
            target.addVariable(variable);
        }
        for (EventDescriptor event : model.events()) {
            if (target.events().containsKey(event.name())) {
                warnings.add(new BlueprintMergeWarning(
                        BlueprintMergeWarning.KIND_EVENT,
                        event.name(),
                        previousBlueprintId,
                        model.id()
                ));
            }
            target.addEvent(event);
        }
        for (FunctionDescriptor function : model.functions()) {
            if (target.functions().containsKey(function.name())) {
                warnings.add(new BlueprintMergeWarning(
                        BlueprintMergeWarning.KIND_FUNCTION,
                        function.name(),
                        previousBlueprintId,
                        model.id()
                ));
            }
            target.addFunction(function);
        }
    }

    private void validateBlueprintType(BlueprintDefinition model) {
        if (model.type() == BlueprintType.MIXIN && model.targetObjectType() == null) {
            throw new BlueprintException("Mixin Blueprints require targetObjectType");
        }
        if (model.type() == BlueprintType.INSTANCE && model.targetObjectType() == null) {
            throw new BlueprintException("INSTANCE blueprints require targetObjectType");
        }
    }

    private void assertSuitable(BlueprintDefinition model, PlatformObject target) {
        if (!isObjectTypeCompatible(model, target)) {
            throw new BlueprintException("Blueprint " + model.name() + " is not suitable for object " + target.path());
        }
        if (!model.suitabilityExpression().isBlank() && !evaluateSuitabilityExpression(model, target)) {
            throw new BlueprintException(
                    "Blueprint " + model.name() + " applicability expression failed for object " + target.path()
            );
        }
    }

    private boolean isSuitableForAutoApply(BlueprintDefinition model, PlatformObject target) {
        if (!isObjectTypeCompatible(model, target)) {
            return false;
        }
        if (model.suitabilityExpression().isBlank()) {
            return false;
        }
        return evaluateSuitabilityExpression(model, target);
    }

    private boolean isObjectTypeCompatible(BlueprintDefinition model, PlatformObject target) {
        return model.targetObjectType() == null || target.type() == model.targetObjectType();
    }

    private boolean evaluateSuitabilityExpression(BlueprintDefinition model, PlatformObject target) {
        try {
            Object result = expressionEngine.evaluate(model.suitabilityExpression(), target);
            if (result instanceof Boolean bool) {
                return bool;
            }
            return Boolean.parseBoolean(String.valueOf(result));
        } catch (Exception e) {
            return false;
        }
    }
}
