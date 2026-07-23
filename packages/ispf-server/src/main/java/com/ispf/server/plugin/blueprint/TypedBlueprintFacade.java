package com.ispf.server.plugin.blueprint;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.PlatformObject;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared CRUD and operations for typed blueprint API facades.
 */
public class TypedBlueprintFacade {

    private final BlueprintType blueprintType;
    private final BlueprintRegistry blueprintRegistry;
    private final BlueprintEngine blueprintEngine;
    private final BlueprintPersistenceService blueprintPersistence;
    private final BlueprintApplicationService blueprintApplicationService;
    private final ObjectManager objectManager;

    public TypedBlueprintFacade(
            BlueprintType blueprintType,
            BlueprintRegistry blueprintRegistry,
            BlueprintEngine blueprintEngine,
            BlueprintPersistenceService blueprintPersistence,
            BlueprintApplicationService blueprintApplicationService,
            ObjectManager objectManager
    ) {
        this.blueprintType = blueprintType;
        this.blueprintRegistry = blueprintRegistry;
        this.blueprintEngine = blueprintEngine;
        this.blueprintPersistence = blueprintPersistence;
        this.blueprintApplicationService = blueprintApplicationService;
        this.objectManager = objectManager;
    }

    public List<BlueprintDto> list() {
        return blueprintRegistry.all().stream()
                .filter(model -> model.type() == blueprintType)
                .filter(model -> ! com.ispf.plugin.blueprint.SystemIntrinsicBlueprints.isIntrinsic(model))
                .map(BlueprintDto::from)
                .toList();
    }

    public List<BlueprintDto> listForCreate(ObjectType platformType, String parentPath) {
        return blueprintRegistry.all().stream()
                .filter(model -> model.type() == blueprintType)
                .filter(model -> ! com.ispf.plugin.blueprint.SystemIntrinsicBlueprints.isIntrinsic(model))
                .filter(model -> platformType == null || model.targetObjectType() == platformType)
                .map(BlueprintDto::from)
                .toList();
    }

    public BlueprintDto get(String id) {
        return BlueprintDto.from(requireTyped(id));
    }

    public BlueprintDto getByName(String name) {
        BlueprintDefinition model = blueprintRegistry.requireByName(name);
        assertType(model);
        return BlueprintDto.from(model);
    }

    public BlueprintDto create(CreatePayload request) {
        validateCreate(request);
        Instant now = Instant.now();
        BlueprintDefinition model = new BlueprintDefinition(
                UUID.randomUUID().toString(),
                request.name(),
                request.description(),
                blueprintType,
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

    public BlueprintDto update(String id, UpdatePayload request) {
        BlueprintDefinition existing = requireTyped(id);
        BlueprintDefinition updated = new BlueprintDefinition(
                existing.id(),
                request.name() != null ? request.name() : existing.name(),
                request.description() != null ? request.description() : existing.description(),
                blueprintType,
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
        objectManager.persistNodeTree(saved.catalogObjectPath());
        return BlueprintDto.from(saved);
    }

    public void delete(String id) {
        requireTyped(id);
        blueprintEngine.deleteBlueprint(id);
        blueprintPersistence.delete(id);
    }

    public BlueprintAttachmentDto apply(String id, String objectPath) {
        requireTyped(id);
        try {
            return BlueprintAttachmentDto.from(blueprintApplicationService.applyBlueprintWithRules(id, objectPath));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    public ObjectDto instantiate(String id, InstantiatePayload request) {
        if (blueprintType != BlueprintType.INSTANCE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "instantiate is only supported for INSTANCE blueprints");
        }
        requireTyped(id);
        try {
            blueprintApplicationService.instantiateWithRules(
                    id,
                    request.parentPath(),
                    request.instanceName(),
                    request.parameters() != null ? request.parameters() : Map.of()
            );
            String path = objectManager.tree().resolveChildPath(request.parentPath(), request.instanceName());
            return ObjectDto.from(objectManager.require(path));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    public PlatformObject singletonInstance(String id) {
        if (blueprintType != BlueprintType.SINGLETON) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "singleton instance is only for Singleton Blueprints");
        }
        BlueprintDefinition model = requireTyped(id);
        return blueprintEngine.ensureSingletonInstance(model);
    }

    private BlueprintDefinition requireTyped(String id) {
        BlueprintDefinition model = blueprintRegistry.requireById(id);
        assertType(model);
        return model;
    }

    private void assertType(BlueprintDefinition model) {
        if (model.type() != blueprintType) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Blueprint " + model.name() + " is not a " + blueprintType + " blueprint"
            );
        }
    }

    private void validateCreate(CreatePayload request) {
        if (blueprintType == BlueprintType.INSTANCE || blueprintType == BlueprintType.MIXIN) {
            if (request.targetObjectType() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, blueprintType + " blueprints require targetObjectType");
            }
        }
        if (blueprintType == BlueprintType.INSTANCE && request.name() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
    }

    public record CreatePayload(
            String name,
            String description,
            ObjectType targetObjectType,
            String suitabilityExpression,
            List<BlueprintVariableDefinition> variables,
            List<EventDescriptor> events,
            List<FunctionDescriptor> functions,
            List<com.ispf.plugin.blueprint.BlueprintBindingRule> bindings,
            Map<String, String> parameters
    ) {
    }

    public record UpdatePayload(
            String name,
            String description,
            ObjectType targetObjectType,
            String suitabilityExpression,
            List<BlueprintVariableDefinition> variables,
            List<EventDescriptor> events,
            List<FunctionDescriptor> functions,
            List<com.ispf.plugin.blueprint.BlueprintBindingRule> bindings,
            Map<String, String> parameters
    ) {
    }

    public record InstantiatePayload(
            String parentPath,
            String instanceName,
            Map<String, String> parameters
    ) {
    }
}
