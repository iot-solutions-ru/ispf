package com.ispf.server.object;

import com.ispf.core.binding.BindingRulesConstants;
import com.ispf.core.model.DataRecord;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.plugin.blueprint.BlueprintEngine;
import com.ispf.server.function.java.JavaFunctionRuntimeService;
import com.ispf.server.history.VariableHistoryService;
import com.ispf.server.persistence.ObjectEntityMapper;
import com.ispf.server.persistence.ObjectNodeRepository;
import com.ispf.server.persistence.ObjectVariableRepository;
import com.ispf.server.persistence.entity.ObjectNodeEntity;
import com.ispf.server.persistence.entity.ObjectVariableEntity;
import com.ispf.server.platform.analytics.engine.AnalyticsTagCatalogService;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final VariableHistoryService historyService;
    private final BindingRulesService bindingRulesService;
    private final BindingDependencyIndex dependencyIndex;
    private final ObjectBindingStatePort bindingStatePort;
    private final BlueprintEngine blueprintEngine;
    private final JavaFunctionRuntimeService javaFunctionRuntimeService;
    private final AnalyticsTagCatalogService tagCatalogService;

    public ObjectTreeLoadSyncService(
            @Lazy ObjectManager objectManager,
            ObjectNodeRepository nodeRepository,
            ObjectVariableRepository variableRepository,
            ObjectEntityMapper mapper,
            @Lazy VariableHistoryService historyService
    ) {
        this(
                objectManager,
                nodeRepository,
                variableRepository,
                mapper,
                historyService,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    @Autowired
    public ObjectTreeLoadSyncService(
            @Lazy ObjectManager objectManager,
            ObjectNodeRepository nodeRepository,
            ObjectVariableRepository variableRepository,
            ObjectEntityMapper mapper,
            @Lazy VariableHistoryService historyService,
            @Lazy BindingRulesService bindingRulesService,
            @Lazy BindingDependencyIndex dependencyIndex,
            @Lazy ObjectBindingStatePort bindingStatePort,
            @Lazy BlueprintEngine blueprintEngine,
            @Lazy JavaFunctionRuntimeService javaFunctionRuntimeService,
            @Lazy AnalyticsTagCatalogService tagCatalogService
    ) {
        this.objectManager = objectManager;
        this.nodeRepository = nodeRepository;
        this.variableRepository = variableRepository;
        this.mapper = mapper;
        this.historyService = historyService;
        this.bindingRulesService = bindingRulesService;
        this.dependencyIndex = dependencyIndex;
        this.bindingStatePort = bindingStatePort;
        this.blueprintEngine = blueprintEngine;
        this.javaFunctionRuntimeService = javaFunctionRuntimeService;
        this.tagCatalogService = tagCatalogService;
    }

    /** Callers hold {@code ObjectManager.treeSyncLock} (write); no additional monitor here. */
    public void reloadFromDatabase(Runnable ensureBootstrapNodes) {
        clearNonRootNodes();
        loadFromDatabase();
        ensureBootstrapNodes.run();
    }

    /** Locking is owned by {@code ObjectManager} (shared tree lock + per-path monitor). */
    public void syncPathFromDatabase(String path) {
        reloadPathFromDatabase(path);
    }

    /**
     * Reloads node metadata and all persisted variables from PostgreSQL (cluster follower sync).
     * Removes RAM variables absent in PG; drops the path from RAM when the node no longer exists.
     */
    public void reloadPathFromDatabase(String path) {
        ObjectTree objectTree = objectManager.tree();
        if (path == null || path.isBlank() || "root".equals(path)) {
            return;
        }
        Optional<ObjectNodeEntity> entityOpt = nodeRepository.findByPath(path);
        if (entityOpt.isEmpty()) {
            objectManager.removePathFromMemoryIfPresent(path);
            forgetPath(path);
            return;
        }
        List<ObjectVariableEntity> variableEntities =
                loadVariablesByPath(List.of(path)).getOrDefault(path, List.of());
        Optional<PlatformObject> existing = objectTree.findByPath(path);
        if (existing.isEmpty()) {
            registerNodeFromEntity(entityOpt.get(), variableEntities);
            invalidateRulesCache(path);
            return;
        }
        applyEntityToExistingNode(existing.get(), entityOpt.get(), variableEntities);
        invalidateRulesCache(path);
    }

    /** Reloads a persisted config variable from PostgreSQL (cluster follower sync). */
    public void syncVariableFromDatabase(String path, String name) {
        ObjectTree objectTree = objectManager.tree();
        if (path == null || path.isBlank() || "root".equals(path) || name == null || name.isBlank()) {
            return;
        }
        Optional<ObjectVariableEntity> entityOpt = variableRepository.findByObjectPathAndName(path, name);
        if (entityOpt.isEmpty()) {
            objectTree.findByPath(path).ifPresent(node -> node.removeVariable(name));
            forgetVariable(path, name);
            if (BindingRulesConstants.RULES_VARIABLE.equals(name)) {
                invalidateRulesCache(path);
            }
            return;
        }
        if (objectTree.findByPath(path).isEmpty()) {
            syncPathFromDatabase(path);
            return;
        }
        upsertVariableFromEntity(objectTree.require(path), entityOpt.get());
        if (BindingRulesConstants.RULES_VARIABLE.equals(name)) {
            invalidateRulesCache(path);
        }
    }

    /** Refreshes derived rule/function/state indexes outside {@code ObjectManager}'s per-path lock. */
    public void afterPathReloaded(String path) {
        if (path == null || path.isBlank() || "root".equals(path)) {
            return;
        }
        Optional<PlatformObject> loaded = objectManager.tree().findByPath(path);
        if (loaded.isEmpty()) {
            evictSubtreeCaches(path);
            return;
        }
        invalidateRulesCache(path);
        if (bindingStatePort != null) {
            bindingStatePort.invalidateCache(path);
        }
        if (dependencyIndex != null) {
            try {
                dependencyIndex.rebuild(path);
            } catch (com.ispf.core.object.ObjectNotFoundException ignored) {
                dependencyIndex.removeSubtree(path);
            }
        }
        if (javaFunctionRuntimeService != null) {
            javaFunctionRuntimeService.syncObjectFunctions(path, loaded.get().functions());
        }
        if (tagCatalogService != null && loaded.get().getVariable(BindingRulesConstants.RULES_VARIABLE).isPresent()) {
            tagCatalogService.invalidateCatalog();
        }
    }

    /** Refreshes derived rule/state indexes after a single config variable sync outside the per-path lock. */
    public void afterVariableSynced(String path, String name) {
        if (path == null || path.isBlank() || "root".equals(path) || name == null || name.isBlank()) {
            return;
        }
        if (BindingRulesConstants.RULES_VARIABLE.equals(name)) {
            invalidateRulesCache(path);
            if (dependencyIndex != null) {
                if (objectManager.tree().findByPath(path).isPresent()) {
                    try {
                        dependencyIndex.rebuild(path);
                    } catch (com.ispf.core.object.ObjectNotFoundException ignored) {
                        dependencyIndex.removeSubtree(path);
                    }
                } else {
                    dependencyIndex.removeSubtree(path);
                }
            }
            if (tagCatalogService != null) {
                tagCatalogService.invalidateCatalog();
            }
            return;
        }
        if (BindingStateVariables.BINDING_STATE.equals(name) && bindingStatePort != null) {
            bindingStatePort.invalidateCache(path);
        }
    }

    /** Rebuilds derived caches after a full {@link #reloadFromDatabase} outside the write lock. */
    public void afterFullReload() {
        invalidateRulesCache(null);
        if (bindingStatePort != null) {
            bindingStatePort.invalidateCache(null);
        }
        List<PlatformObject> nodes = objectManager.tree().all();
        if (dependencyIndex != null) {
            List<String> rulePaths = nodes.stream()
                    .filter(node -> node.getVariable(BindingRulesConstants.RULES_VARIABLE).isPresent())
                    .map(PlatformObject::path)
                    .toList();
            dependencyIndex.rebuildAll(rulePaths);
        }
        if (javaFunctionRuntimeService != null) {
            javaFunctionRuntimeService.unregisterSubtree("root");
            for (PlatformObject node : nodes) {
                if (!node.functions().isEmpty()) {
                    javaFunctionRuntimeService.syncObjectFunctions(node.path(), node.functions());
                }
            }
        }
        if (tagCatalogService != null) {
            tagCatalogService.invalidateCatalog();
        }
    }

    /** Evicts in-memory derived state for a deleted object path and its descendants ({@code path.*}). */
    public void evictSubtreeCaches(String path) {
        if (path == null || path.isBlank() || "root".equals(path)) {
            return;
        }
        if (bindingRulesService != null) {
            bindingRulesService.forgetSubtree(path);
        }
        if (bindingStatePort != null) {
            bindingStatePort.forgetSubtree(path);
        }
        if (dependencyIndex != null) {
            dependencyIndex.removeSubtree(path);
        }
        if (blueprintEngine != null) {
            blueprintEngine.removeAttachmentsForDeletedSubtree(path);
        }
        if (javaFunctionRuntimeService != null) {
            javaFunctionRuntimeService.unregisterSubtree(path);
        }
        if (tagCatalogService != null) {
            tagCatalogService.invalidateCatalog();
        }
    }

    private void invalidateRulesCache(String path) {
        if (bindingRulesService != null) {
            bindingRulesService.invalidateRulesCache(path);
        }
    }

    void loadFromDatabase() {
        ObjectTree objectTree = objectManager.tree();
        List<ObjectNodeEntity> nodes = nodeRepository.findAllByOrderByPathAsc();
        for (ObjectNodeEntity entity : nodes) {
            if ("root".equals(entity.getPath())) {
                continue;
            }
            // Always project persisted node config (incl. audit flags) onto RAM.
            // Skipping when the path already exists left bootstrap/blueprint stubs
            // with defaults and wiped runtime settings after restart.
            registerNodeFromEntity(entity, List.of());
            objectTree.ensureParentLink(entity.getPath());
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
        Optional<PlatformObject> existing = objectTree.findByPath(entity.getPath());
        if (existing.isPresent()) {
            applyEntityToExistingNode(existing.get(), entity, variableEntities);
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
                entity.getLastChangedAt(),
                entity.getCreatedAt()
        );
        node.setappliedBlueprintIds(mapper.readappliedBlueprintIds(entity.getappliedBlueprintIdsJson()));
        node.setBlueprintContributions(mapper.readBlueprintContributions(entity.getBlueprintContributionsJson()));
        node.setBindingAuditEnabled(entity.isBindingAuditEnabled());
        node.setFunctionAuditEnabled(entity.isFunctionAuditEnabled());
        node.setEventJournalEnabled(entity.isEventJournalEnabled());
        for (EventDescriptor event : mapper.readEvents(entity.getEventsJson())) {
            node.addEvent(event);
        }
        for (FunctionDescriptor function : mapper.readFunctions(entity.getFunctionsJson())) {
            node.addFunction(function);
        }
        objectTree.register(node);
        for (ObjectVariableEntity varEntity : variableEntities) {
            if (node.variables().containsKey(varEntity.getName())) {
                continue;
            }
            node.addVariable(mapper.toVariable(varEntity));
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
        if (objectManager.tree().findByPath(node.path()).isPresent()) {
            objectManager.tree().updateNodeType(node.path(), entity.getType());
        } else {
            node.setType(entity.getType());
        }
        node.updateInfo(entity.getDisplayName(), entity.getDescription());
        node.setSortOrder(entity.getSortOrder());
        node.setRevision(entity.getRevision());
        node.setLastChangedBy(entity.getLastChangedBy());
        node.setLastChangedAt(entity.getLastChangedAt());
        node.setappliedBlueprintIds(mapper.readappliedBlueprintIds(entity.getappliedBlueprintIdsJson()));
        node.setBlueprintContributions(mapper.readBlueprintContributions(entity.getBlueprintContributionsJson()));
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
                forgetVariable(node.path(), ramName);
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

    private void forgetPath(String path) {
        if (historyService != null) {
            historyService.forgetPath(path);
        }
    }

    private void forgetVariable(String path, String name) {
        if (historyService != null) {
            historyService.forgetVariable(path, name);
        }
    }

    private void clearNonRootNodes() {
        ObjectTree objectTree = objectManager.tree();
        List<String> paths = objectTree.all().stream()
                .map(PlatformObject::path)
                .filter(path -> !"root".equals(path))
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        for (String path : paths) {
            if (objectTree.findByPath(path).isPresent()) {
                objectTree.delete(path);
            }
            forgetPath(path);
        }
        invalidateRulesCache(null);
        if (bindingStatePort != null) {
            bindingStatePort.invalidateCache(null);
        }
        if (blueprintEngine != null) {
            blueprintEngine.removeAttachmentsForDeletedSubtree("root");
        }
    }
}
