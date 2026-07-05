package com.ispf.server.plugin.blueprint;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.plugin.blueprint.BlueprintAttachment;
import com.ispf.plugin.blueprint.BlueprintCatalogRoots;
import com.ispf.plugin.blueprint.BlueprintBindingRule;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintEngine;
import com.ispf.plugin.blueprint.BlueprintException;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.plugin.blueprint.BlueprintType;
import com.ispf.plugin.blueprint.BlueprintVariableDefinition;
import com.ispf.server.api.dto.ObjectDto;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.blueprint.dto.BlueprintAttachmentDto;
import com.ispf.server.plugin.blueprint.dto.BlueprintDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API for the Models plugin.
 */
@RestController
@RequestMapping("/api/v1/blueprints")
public class BlueprintController {

    private final BlueprintRegistry blueprintRegistry;
    private final BlueprintEngine blueprintEngine;
    private final ObjectManager objectManager;
    private final BlueprintPersistenceService blueprintPersistence;
    private final BlueprintMergeService BlueprintMergeService;
    private final BlueprintApplicationService blueprintApplicationService;

    public BlueprintController(
            BlueprintRegistry blueprintRegistry,
            BlueprintEngine blueprintEngine,
            ObjectManager objectManager,
            BlueprintPersistenceService blueprintPersistence,
            BlueprintMergeService BlueprintMergeService,
            BlueprintApplicationService blueprintApplicationService
    ) {
        this.blueprintRegistry = blueprintRegistry;
        this.blueprintEngine = blueprintEngine;
        this.objectManager = objectManager;
        this.blueprintPersistence = blueprintPersistence;
        this.BlueprintMergeService = BlueprintMergeService;
        this.blueprintApplicationService = blueprintApplicationService;
    }

    @GetMapping
    public List<BlueprintDto> list() {
        return blueprintRegistry.all().stream()
                .map(BlueprintDto::from)
                .toList();
    }

    @GetMapping("/{id}")
    public BlueprintDto get(@PathVariable String id) {
        return BlueprintDto.from(blueprintRegistry.requireById(id));
    }

    @GetMapping("/by-name/{name}")
    public BlueprintDto getByName(@PathVariable String name) {
        return BlueprintDto.from(blueprintRegistry.requireByName(name));
    }

    @PostMapping
    public BlueprintDto create(@Valid @RequestBody CreateModelRequest request) {
        Instant now = Instant.now();
        BlueprintDefinition model = new BlueprintDefinition(
                UUID.randomUUID().toString(),
                request.name(),
                request.description(),
                request.type(),
                request.targetObjectType(),
                request.suitabilityExpression(),
                request.variables(),
                request.events(),
                request.functions(),
                request.bindings(),
                request.parameters(),
                now,
                now
        );
        try {
            BlueprintDefinition created = blueprintEngine.createBlueprint(model);
            blueprintPersistence.persist(created, false);
            return BlueprintDto.from(created);
        } catch (BlueprintException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public BlueprintDto update(@PathVariable String id, @Valid @RequestBody UpdateModelRequest request) {
        BlueprintDefinition existing = blueprintRegistry.requireById(id);
        BlueprintDefinition updated = new BlueprintDefinition(
                existing.id(),
                request.name() != null ? request.name() : existing.name(),
                request.description() != null ? request.description() : existing.description(),
                request.type() != null ? request.type() : existing.type(),
                request.targetObjectType() != null ? request.targetObjectType() : existing.targetObjectType(),
                request.suitabilityExpression() != null ? request.suitabilityExpression() : existing.suitabilityExpression(),
                request.variables() != null ? request.variables() : existing.variables(),
                request.events() != null ? request.events() : existing.events(),
                request.functions() != null ? request.functions() : existing.functions(),
                request.bindings() != null ? request.bindings() : existing.bindings(),
                request.parameters() != null ? request.parameters() : existing.parameters(),
                existing.createdAt(),
                Instant.now()
        );
        BlueprintDefinition saved = blueprintEngine.updateBlueprint(updated);
        blueprintPersistence.persist(saved, false);
        return BlueprintDto.from(saved);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        blueprintEngine.deleteBlueprint(id);
        blueprintPersistence.delete(id);
    }

    @PostMapping("/{id}/apply")
    public BlueprintAttachmentDto apply(
            @PathVariable String id,
            @RequestParam @NotBlank String objectPath
    ) {
        try {
            BlueprintAttachmentDto attachment = BlueprintAttachmentDto.from(
                    blueprintApplicationService.applyBlueprintWithRules(id, objectPath)
            );
            return attachment;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/{id}/instantiate")
    public ObjectDto instantiate(
            @PathVariable String id,
            @Valid @RequestBody InstantiateModelRequest request
    ) {
        try {
            blueprintApplicationService.instantiateWithRules(
                    id,
                    request.parentPath(),
                    request.instanceName(),
                    request.parameters() != null ? request.parameters() : Map.of()
            );
            String path = objectManager.tree().resolveChildPath(request.parentPath(), request.instanceName());
            return ObjectDto.from(
                    objectManager.require(path),
                    null,
                    com.ispf.server.plugin.blueprint.dto.AppliedBlueprintDto.resolve(
                            objectManager.require(path),
                            blueprintRegistry
                    )
            );
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/from-object")
    public BlueprintDto createFromObject(@Valid @RequestBody CreateFromObjectRequest request) {
        try {
            BlueprintDefinition model = blueprintEngine.createFromObject(
                    request.sourcePath(),
                    request.blueprintName(),
                    request.description(),
                    request.type() != null ? request.type() : BlueprintType.INSTANCE
            );
            blueprintPersistence.persist(model, false);
            objectManager.persistNodeTree(model.catalogObjectPath());
            return BlueprintDto.from(model);
        } catch (BlueprintException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/attachments")
    public List<BlueprintAttachmentDto> attachments(@RequestParam(required = false) String objectPath) {
        List<BlueprintAttachment> list = objectPath != null && !objectPath.isBlank()
                ? blueprintEngine.attachmentsForObject(objectPath)
                : blueprintEngine.attachments();
        return list.stream().map(BlueprintAttachmentDto::from).toList();
    }

    @PostMapping("/{id}/upgrade")
    public Map<String, Object> upgradeModel(
            @PathVariable String id,
            @RequestParam @NotBlank String targetPath,
            @RequestParam(required = false) String targetVersion
    ) {
        try {
            BlueprintDefinition model = blueprintRegistry.requireById(id);
            if (targetVersion != null && !targetVersion.isBlank()
                    && !targetVersion.equals(model.blueprintVersion())) {
                throw new BlueprintException(
                        "Model version mismatch: requested " + targetVersion + ", actual " + model.blueprintVersion()
                );
            }
            BlueprintAttachmentDto attachment = BlueprintAttachmentDto.from(
                    blueprintApplicationService.applyBlueprintWithRules(id, targetPath)
            );
            return Map.of(
                    "status", "OK",
                    "targetPath", targetPath,
                    "blueprintVersion", model.blueprintVersion(),
                    "attachmentId", attachment.id(),
                    "warnings", attachment.warnings()
            );
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/merge-preview")
    public Map<String, Object> mergePreview(@Valid @RequestBody MergePreviewRequest request) {
        return BlueprintMergeService.mergePreview(
                request.baseModelId(),
                request.theirsModelId(),
                request.objectPath()
        );
    }

    @PostMapping("/merge-apply")
    public Map<String, Object> mergeApply(@Valid @RequestBody MergeApplyRequest request) {
        return BlueprintMergeService.applyMerge(
                request.baseModelId(),
                request.theirsModelId(),
                request.objectPath(),
                request.resolutions()
        );
    }

    @PostMapping("/{id}/upgrade-instances")
    public Map<String, Object> upgradeInstances(
            @PathVariable String id,
            @RequestParam(defaultValue = "false") boolean dryRun
    ) {
        try {
            BlueprintDefinition model = blueprintRegistry.requireById(id);
            LinkedHashSet<String> targets = new LinkedHashSet<>();
            for (BlueprintAttachment attachment : blueprintEngine.attachments()) {
                if (id.equals(attachment.blueprintId())) {
                    targets.add(attachment.objectPath());
                }
            }
            for (com.ispf.core.object.PlatformObject node : objectManager.tree().all()) {
                if (blueprintEngine.isBlueprintCatalogPath(node.path())) {
                    continue;
                }
                if (model.type() == com.ispf.plugin.blueprint.BlueprintType.INSTANCE
                        && node.type() != model.targetObjectType()) {
                    continue;
                }
                node.templateId().ifPresent(templateId -> {
                    if (id.equals(templateId) || model.name().equals(templateId)) {
                        targets.add(node.path());
                    }
                });
            }
            List<String> upgraded = new ArrayList<>();
            for (String targetPath : targets) {
                if (dryRun) {
                    upgraded.add(targetPath);
                    continue;
                }
                blueprintApplicationService.applyBlueprintWithRules(id, targetPath);
                upgraded.add(targetPath);
            }
            return Map.of(
                    "status", dryRun ? "DRY_RUN" : "OK",
                    "dryRun", dryRun,
                    "blueprintVersion", model.blueprintVersion(),
                    "upgraded", upgraded,
                    "count", upgraded.size()
            );
        } catch (BlueprintException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/{id}/diff")
    public Map<String, Object> diff(
            @PathVariable String id,
            @RequestParam @NotBlank String objectPath
    ) {
        BlueprintDefinition model = blueprintRegistry.requireById(id);
        PlatformObject object = objectManager.require(objectPath);
        LinkedHashSet<String> modelVariables = model.variables().stream()
                .map(BlueprintVariableDefinition::name)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<String> objectVariables = new LinkedHashSet<>(object.variables().keySet());
        LinkedHashSet<String> modelEvents = model.events().stream()
                .map(EventDescriptor::name)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<String> objectEvents = new LinkedHashSet<>(object.events().keySet());
        LinkedHashSet<String> modelFunctions = model.functions().stream()
                .map(FunctionDescriptor::name)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<String> objectFunctions = new LinkedHashSet<>(object.functions().keySet());

        List<String> variablesToAdd = modelVariables.stream()
                .filter(name -> !objectVariables.contains(name))
                .toList();
        List<String> variablesOnlyOnObject = objectVariables.stream()
                .filter(name -> !modelVariables.contains(name))
                .toList();
        List<String> eventsToAdd = modelEvents.stream()
                .filter(name -> !objectEvents.contains(name))
                .toList();
        List<String> functionsToAdd = modelFunctions.stream()
                .filter(name -> !objectFunctions.contains(name))
                .toList();

        return Map.of(
                "objectPath", objectPath,
                "blueprintVersion", model.blueprintVersion(),
                "variablesToAdd", variablesToAdd,
                "variablesOnlyOnObject", variablesOnlyOnObject,
                "eventsToAdd", eventsToAdd,
                "functionsToAdd", functionsToAdd,
                "bindingsCount", model.bindings().size()
        );
    }

    @GetMapping("/{id}/instances")
    public List<Map<String, String>> listInstances(@PathVariable String id) {
        BlueprintDefinition model = blueprintRegistry.requireById(id);
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        for (BlueprintAttachment attachment : blueprintEngine.attachments()) {
            if (id.equals(attachment.blueprintId())) {
                paths.add(attachment.objectPath());
            }
        }
        for (com.ispf.core.object.PlatformObject node : objectManager.tree().all()) {
            if (BlueprintCatalogRoots.isCatalogPath(node.path()) || BlueprintCatalogRoots.isLegacyPath(node.path())) {
                continue;
            }
            if (model.type() == com.ispf.plugin.blueprint.BlueprintType.INSTANCE
                    && node.type() != model.targetObjectType()) {
                continue;
            }
            node.templateId().ifPresent(templateId -> {
                if (id.equals(templateId) || model.name().equals(templateId)) {
                    paths.add(node.path());
                }
            });
        }
        return paths.stream()
                .map(path -> Map.of("objectPath", path))
                .toList();
    }

    public record CreateModelRequest(
            @NotBlank String name,
            String description,
            BlueprintType type,
            ObjectType targetObjectType,
            String suitabilityExpression,
            List<BlueprintVariableDefinition> variables,
            List<EventDescriptor> events,
            List<FunctionDescriptor> functions,
            List<BlueprintBindingRule> bindings,
            Map<String, String> parameters
    ) {
    }

    public record UpdateModelRequest(
            String name,
            String description,
            BlueprintType type,
            ObjectType targetObjectType,
            String suitabilityExpression,
            List<BlueprintVariableDefinition> variables,
            List<EventDescriptor> events,
            List<FunctionDescriptor> functions,
            List<BlueprintBindingRule> bindings,
            Map<String, String> parameters
    ) {
    }

    public record InstantiateModelRequest(
            @NotBlank String parentPath,
            @NotBlank String instanceName,
            Map<String, String> parameters
    ) {
    }

    public record CreateFromObjectRequest(
            @NotBlank String sourcePath,
            @NotBlank String blueprintName,
            String description,
            BlueprintType type
    ) {
    }

    public record MergePreviewRequest(
            @NotBlank String baseModelId,
            @NotBlank String theirsModelId,
            @NotBlank String objectPath
    ) {
    }

    public record MergeApplyRequest(
            @NotBlank String baseModelId,
            @NotBlank String theirsModelId,
            @NotBlank String objectPath,
            List<BlueprintMergeService.MergeResolution> resolutions
    ) {
    }
}
