package com.ispf.server.object;

import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.bootstrap.PlatformCatalogSortOrder;
import com.ispf.server.bootstrap.SystemObjectDescriptions;
import com.ispf.server.persistence.ObjectEntityMapper;
import com.ispf.server.persistence.ObjectNodeRepository;
import com.ispf.server.persistence.ObjectVariableRepository;
import com.ispf.server.persistence.entity.ObjectNodeEntity;
import com.ispf.server.persistence.entity.ObjectVariableEntity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tree structure CRUD: create / delete / reorder / persistNodeTree and catalog folder helpers.
 * Extracted from {@link ObjectManager} (ADR-0048 Wave 4).
 */
@Service
public class TreeCrudService {

    private final ObjectManager objectManager;
    private final ObjectNodeRepository nodeRepository;
    private final ObjectVariableRepository variableRepository;
    private final ObjectEntityMapper mapper;
    private final ObjectProvider<VisualGroupService> visualGroupService;

    public TreeCrudService(
            @Lazy ObjectManager objectManager,
            ObjectNodeRepository nodeRepository,
            ObjectVariableRepository variableRepository,
            ObjectEntityMapper mapper,
            ObjectProvider<VisualGroupService> visualGroupService
    ) {
        this.objectManager = objectManager;
        this.nodeRepository = nodeRepository;
        this.variableRepository = variableRepository;
        this.mapper = mapper;
        this.visualGroupService = visualGroupService;
    }

    @Transactional
    public PlatformObject create(
            String parentPath,
            String name,
            ObjectType type,
            String displayName,
            String description,
            String templateId
    ) {
        var objectTree = objectManager.tree();
        String fullPath = objectTree.resolveChildPath(parentPath, name);
        if (objectTree.findByPath(fullPath).isPresent()) {
            throw new IllegalArgumentException("Object already exists: " + fullPath);
        }
        PlatformObject node = new PlatformObject(
                UUID.randomUUID().toString(),
                fullPath,
                type,
                displayName,
                description,
                templateId,
                PlatformCatalogSortOrder.forPath(fullPath).orElseGet(() -> nextSortOrder(parentPath))
        );
        objectTree.register(node);
        node.setRevision(1L);
        node.setLastChangedBy(ObjectRevisionContext.actor());
        node.setLastChangedAt(Instant.now());
        objectManager.persistNode(node);
        objectManager.publish(ObjectChangeEvent.of(ObjectChangeType.CREATED, fullPath, node.revision(), node.lastChangedBy()));
        return node;
    }

    @Transactional
    public void reorderChildren(String parentPath, List<String> orderedPaths) {
        if (parentPath == null || parentPath.isBlank()) {
            throw new IllegalArgumentException("parentPath is required");
        }
        var objectTree = objectManager.tree();
        objectTree.require(parentPath);
        List<PlatformObject> children = structuralChildrenOf(parentPath);
        if (children.isEmpty()) {
            throw new IllegalArgumentException("No children under: " + parentPath);
        }
        if (orderedPaths == null || orderedPaths.size() != children.size()) {
            throw new IllegalArgumentException("orderedPaths must list all direct children");
        }
        Set<String> expected = children.stream().map(PlatformObject::path).collect(Collectors.toSet());
        Set<String> provided = new LinkedHashSet<>(orderedPaths);
        if (!expected.equals(provided)) {
            throw new IllegalArgumentException("orderedPaths must match direct children of " + parentPath);
        }
        for (int index = 0; index < orderedPaths.size(); index++) {
            String path = orderedPaths.get(index);
            PlatformObject node = objectTree.require(path);
            node.setSortOrder(index);
            objectManager.persistNode(node);
        }
        objectManager.publish(ObjectChangeEvent.of(ObjectChangeType.UPDATED, parentPath));
    }

    /** Direct children visible in the structural tree (excludes visual-group members). */
    List<PlatformObject> structuralChildrenOf(String parentPath) {
        VisualGroupService groups = visualGroupService.getIfAvailable();
        return objectManager.tree().childrenOf(parentPath).stream()
                .filter(node -> groups == null || !groups.isHiddenFromStructuralTree(node.path()))
                .toList();
    }

    @Transactional
    public PlatformObject reconcileType(String path, ObjectType expectedType) {
        PlatformObject node = objectManager.tree().require(path);
        if (node.type() != expectedType) {
            node.setType(expectedType);
            objectManager.persistNode(node);
            objectManager.publish(ObjectChangeEvent.of(ObjectChangeType.UPDATED, path));
        }
        return node;
    }

    @Transactional
    public void ensureSystemCatalogFolder(String path, ObjectType type, String templateId) {
        SystemObjectDescriptions.Entry entry = SystemObjectDescriptions.resolve(path)
                .orElseThrow(() -> new IllegalStateException("Missing system description: " + path));
        if (objectManager.tree().findByPath(path).isPresent()) {
            objectManager.updateInfo(path, entry.displayName(), entry.description());
            reconcileType(path, type);
            return;
        }
        Optional<ObjectNodeEntity> persisted = nodeRepository.findByPath(path);
        if (persisted.isPresent()) {
            attachPersistedNode(persisted.get());
            objectManager.updateInfo(path, entry.displayName(), entry.description());
            reconcileType(path, type);
            return;
        }
        int lastDot = path.lastIndexOf('.');
        create(
                path.substring(0, lastDot),
                path.substring(lastDot + 1),
                type,
                entry.displayName(),
                entry.description(),
                templateId
        );
    }

    private void attachPersistedNode(ObjectNodeEntity entity) {
        if (objectManager.tree().findByPath(entity.getPath()).isPresent()) {
            return;
        }
        PlatformObject node = new PlatformObject(
                entity.getId(),
                entity.getPath(),
                entity.getType(),
                entity.getDisplayName(),
                entity.getDescription(),
                entity.getTemplateId(),
                entity.getSortOrder(),
                entity.getRevision(),
                entity.getLastChangedBy(),
                entity.getLastChangedAt()
        );
        node.setappliedBlueprintIds(mapper.readappliedBlueprintIds(entity.getappliedBlueprintIdsJson()));
        node.setBindingAuditEnabled(entity.isBindingAuditEnabled());
        node.setFunctionAuditEnabled(entity.isFunctionAuditEnabled());
        node.setEventJournalEnabled(entity.isEventJournalEnabled());
        for (EventDescriptor event : mapper.readEvents(entity.getEventsJson())) {
            node.addEvent(event);
        }
        for (FunctionDescriptor function : mapper.readFunctions(entity.getFunctionsJson())) {
            node.addFunction(function);
        }
        objectManager.tree().register(node);
        for (ObjectVariableEntity varEntity : variableRepository.findByObjectPathIn(List.of(entity.getPath()))) {
            node.addVariable(mapper.toVariable(varEntity));
        }
    }

    @Transactional
    public void delete(String path) {
        if (path == null || path.isBlank() || "root".equals(path)) {
            throw new IllegalArgumentException("Cannot delete root object");
        }
        List<ObjectNodeEntity> toDelete = nodeRepository.findByPathPrefixOrderByPathLengthDesc(path);
        if (toDelete.isEmpty() && objectManager.tree().findByPath(path).isEmpty()) {
            throw new ObjectNotFoundException(path);
        }
        objectManager.removePathFromMemoryIfPresent(path);
        for (ObjectNodeEntity entity : toDelete) {
            variableRepository.deleteByObjectPath(entity.getPath());
            nodeRepository.deleteById(entity.getId());
        }
        objectManager.publish(ObjectChangeEvent.of(ObjectChangeType.DELETED, path));
    }

    @Transactional
    public void persistNodeTree(String path) {
        PlatformObject node = objectManager.tree().require(path);
        objectManager.persistNode(node);
        for (Variable variable : node.variables().values()) {
            objectManager.persistVariable(path, variable);
        }
        objectManager.publish(ObjectChangeEvent.of(ObjectChangeType.UPDATED, path));
    }

    private int nextSortOrder(String parentPath) {
        return objectManager.tree().childrenOf(parentPath).stream()
                .mapToInt(PlatformObject::sortOrder)
                .max()
                .orElse(-1) + 1;
    }
}
