package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.persistence.ObjectEntityMapper;
import com.ispf.server.persistence.ObjectNodeRepository;
import com.ispf.server.persistence.ObjectVariableRepository;
import com.ispf.server.persistence.entity.ObjectNodeEntity;
import com.ispf.server.persistence.entity.ObjectVariableEntity;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * DB → RAM tree load / path reload / variable sync used by cluster replica followers
 * and ObjectManager startup. Extracted from {@link ObjectManager} (ADR-0048 Wave 4).
 */
@Service
public class ObjectTreeLoadSyncService {

    private final ObjectManager objectManager;
    private final ObjectNodeRepository nodeRepository;
    private final ObjectVariableRepository variableRepository;
    private final ObjectEntityMapper mapper;

    public ObjectTreeLoadSyncService(
            @Lazy ObjectManager objectManager,
            ObjectNodeRepository nodeRepository,
            ObjectVariableRepository variableRepository,
            ObjectEntityMapper mapper
    ) {
        this.objectManager = objectManager;
        this.nodeRepository = nodeRepository;
        this.variableRepository = variableRepository;
        this.mapper = mapper;
    }

    public synchronized void reloadFromDatabase(Runnable ensureBootstrapNodes) {
        clearNonRootNodes();
        loadFromDatabase();
        ensureBootstrapNodes.run();
    }

    public synchronized void syncPathFromDatabase(String path) {
        reloadPathFromDatabase(path);
    }

    /**
     * Reloads node metadata and all persisted variables from PostgreSQL (cluster follower sync).
     * Removes RAM variables absent in PG; drops the path from RAM when the node no longer exists.
     */
    public synchronized void reloadPathFromDatabase(String path) {
        ObjectTree objectTree = objectManager.tree();
        if (path == null || path.isBlank() || "root".equals(path)) {
            return;
        }
        Optional<ObjectNodeEntity> entityOpt = nodeRepository.findByPath(path);
        if (entityOpt.isEmpty()) {
            objectManager.removePathFromMemoryIfPresent(path);
            return;
        }
        List<ObjectVariableEntity> variableEntities =
                loadVariablesByPath(List.of(path)).getOrDefault(path, List.of());
        Optional<PlatformObject> existing = objectTree.findByPath(path);
        if (existing.isEmpty()) {
            registerNodeFromEntity(entityOpt.get(), variableEntities);
            return;
        }
        applyEntityToExistingNode(existing.get(), entityOpt.get(), variableEntities);
    }

    /** Reloads a persisted config variable from PostgreSQL (cluster follower sync). */
    public synchronized void syncVariableFromDatabase(String path, String name) {
        ObjectTree objectTree = objectManager.tree();
        if (path == null || path.isBlank() || "root".equals(path) || name == null || name.isBlank()) {
            return;
        }
        Optional<ObjectVariableEntity> entityOpt = variableRepository.findByObjectPathAndName(path, name);
        if (entityOpt.isEmpty()) {
            objectTree.findByPath(path).ifPresent(node -> node.removeVariable(name));
            return;
        }
        if (objectTree.findByPath(path).isEmpty()) {
            syncPathFromDatabase(path);
            return;
        }
        upsertVariableFromEntity(objectTree.require(path), entityOpt.get());
    }

    void loadFromDatabase() {
        ObjectTree objectTree = objectManager.tree();
        List<ObjectNodeEntity> nodes = nodeRepository.findAllByOrderByPathAsc();
        for (ObjectNodeEntity entity : nodes) {
            if ("root".equals(entity.getPath())) {
                continue;
            }
            if (objectTree.findByPath(entity.getPath()).isPresent()) {
                objectTree.ensureParentLink(entity.getPath());
                continue;
            }
            registerNodeFromEntity(entity, List.of());
        }
        objectTree.rebuildChildIndex();
        List<String> nodePaths = nodes.stream()
                .map(ObjectNodeEntity::getPath)
                .filter(path -> !"root".equals(path))
                .toList();
        Map<String, List<ObjectVariableEntity>> variablesByPath = loadVariablesByPath(nodePaths);
        for (ObjectNodeEntity entity : nodes) {
            if ("root".equals(entity.getPath())) {
                continue;
            }
            PlatformObject node = objectTree.require(entity.getPath());
            for (ObjectVariableEntity varEntity : variablesByPath.getOrDefault(entity.getPath(), List.of())) {
                if (node.variables().containsKey(varEntity.getName())) {
                    continue;
                }
                node.addVariable(mapper.toVariable(varEntity));
            }
        }
    }

    void registerNodeFromEntity(ObjectNodeEntity entity, List<ObjectVariableEntity> variableEntities) {
        ObjectTree objectTree = objectManager.tree();
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
        for (EventDescriptor event : mapper.readEvents(entity.getEventsJson())) {
            node.addEvent(event);
        }
        for (FunctionDescriptor function : mapper.readFunctions(entity.getFunctionsJson())) {
            node.addFunction(function);
        }
        if (objectTree.findByPath(entity.getPath()).isEmpty()) {
            objectTree.register(node);
        }
        PlatformObject registered = objectTree.require(entity.getPath());
        for (ObjectVariableEntity varEntity : variableEntities) {
            if (registered.variables().containsKey(varEntity.getName())) {
                continue;
            }
            registered.addVariable(mapper.toVariable(varEntity));
        }
    }

    Map<String, List<ObjectVariableEntity>> loadVariablesByPath(List<String> nodePaths) {
        Map<String, List<ObjectVariableEntity>> grouped = new HashMap<>();
        if (nodePaths.isEmpty()) {
            return grouped;
        }
        final int batchSize = 250;
        for (int offset = 0; offset < nodePaths.size(); offset += batchSize) {
            List<String> batch = nodePaths.subList(offset, Math.min(offset + batchSize, nodePaths.size()));
            for (ObjectVariableEntity varEntity : variableRepository.findByObjectPathIn(batch)) {
                grouped.computeIfAbsent(varEntity.getObjectPath(), ignored -> new ArrayList<>()).add(varEntity);
            }
        }
        return grouped;
    }

    private void applyEntityToExistingNode(
            PlatformObject node,
            ObjectNodeEntity entity,
            List<ObjectVariableEntity> variableEntities
    ) {
        node.setType(entity.getType());
        node.updateInfo(entity.getDisplayName(), entity.getDescription());
        node.setSortOrder(entity.getSortOrder());
        node.setRevision(entity.getRevision());
        node.setLastChangedBy(entity.getLastChangedBy());
        node.setLastChangedAt(entity.getLastChangedAt());
        node.setappliedBlueprintIds(mapper.readappliedBlueprintIds(entity.getappliedBlueprintIdsJson()));
        node.setBindingAuditEnabled(entity.isBindingAuditEnabled());
        node.setFunctionAuditEnabled(entity.isFunctionAuditEnabled());
        node.setEventJournalEnabled(entity.isEventJournalEnabled());

        for (String name : new ArrayList<>(node.events().keySet())) {
            node.removeEvent(name);
        }
        for (EventDescriptor event : mapper.readEvents(entity.getEventsJson())) {
            node.addEvent(event);
        }
        for (String name : new ArrayList<>(node.functions().keySet())) {
            node.removeFunction(name);
        }
        for (FunctionDescriptor function : mapper.readFunctions(entity.getFunctionsJson())) {
            node.addFunction(function);
        }

        Set<String> dbVariableNames = new LinkedHashSet<>();
        for (ObjectVariableEntity varEntity : variableEntities) {
            dbVariableNames.add(varEntity.getName());
            upsertVariableFromEntity(node, varEntity);
        }
        for (String ramName : new ArrayList<>(node.variables().keySet())) {
            if (!dbVariableNames.contains(ramName)) {
                node.removeVariable(ramName);
            }
        }
    }

    private void upsertVariableFromEntity(PlatformObject node, ObjectVariableEntity entity) {
        DataRecord value = mapper.readDataRecord(entity.getValueJson());
        node.getVariable(entity.getName()).ifPresentOrElse(
                variable -> variable.setComputedValue(value),
                () -> node.addVariable(mapper.toVariable(entity))
        );
    }

    private void clearNonRootNodes() {
        ObjectTree objectTree = objectManager.tree();
        List<String> paths = objectTree.all().stream()
                .map(PlatformObject::path)
                .filter(path -> !"root".equals(path))
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        for (String path : paths) {
            objectTree.delete(path);
        }
    }
}
