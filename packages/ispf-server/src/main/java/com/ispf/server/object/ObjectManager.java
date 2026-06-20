package com.ispf.server.object;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.Variable;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.expression.BindingEvaluator;
import com.ispf.server.bootstrap.PlatformBootstrap;
import com.ispf.server.persistence.ObjectEntityMapper;
import com.ispf.server.persistence.ObjectNodeRepository;
import com.ispf.server.persistence.ObjectVariableRepository;
import com.ispf.server.persistence.entity.ObjectNodeEntity;
import com.ispf.server.persistence.entity.ObjectVariableEntity;
import com.ispf.server.plugin.model.ModelApplicationRunner;
import com.ispf.server.plugin.model.ModelBootstrap;
import com.ispf.server.plugin.model.ModelPersistenceService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Central service for object tree operations with database persistence and change events.
 */
@Service
public class ObjectManager {

    private final ObjectTree objectTree = new ObjectTree();
    private final ObjectNodeRepository nodeRepository;
    private final ObjectVariableRepository variableRepository;
    private final ObjectEntityMapper mapper;
    private final PlatformBootstrap platformBootstrap;
    private final ObjectProvider<ModelBootstrap> modelBootstrap;
    private final ObjectProvider<ModelApplicationRunner> modelApplicationRunner;
    private final ObjectProvider<ModelPersistenceService> modelPersistence;
    private final ApplicationEventPublisher eventPublisher;
    private final BindingEvaluator bindingEvaluator;
    private volatile boolean initialized;

    public ObjectManager(
            ObjectNodeRepository nodeRepository,
            ObjectVariableRepository variableRepository,
            ObjectEntityMapper mapper,
            PlatformBootstrap platformBootstrap,
            ObjectProvider<ModelBootstrap> modelBootstrap,
            ObjectProvider<ModelApplicationRunner> modelApplicationRunner,
            ObjectProvider<ModelPersistenceService> modelPersistence,
            ApplicationEventPublisher eventPublisher,
            BindingEvaluator bindingEvaluator
    ) {
        this.nodeRepository = nodeRepository;
        this.variableRepository = variableRepository;
        this.mapper = mapper;
        this.platformBootstrap = platformBootstrap;
        this.modelBootstrap = modelBootstrap;
        this.modelApplicationRunner = modelApplicationRunner;
        this.modelPersistence = modelPersistence;
        this.eventPublisher = eventPublisher;
        this.bindingEvaluator = bindingEvaluator;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Transactional
    public void initialize() {
        if (initialized) {
            return;
        }
        if (nodeRepository.existsByPath("root.platform")) {
            loadFromDatabase();
            ensureBootstrapNodes();
        } else {
            seedPlatformStructure();
        }
        modelBootstrap.getObject().ensureBuiltInModels();
        modelPersistence.ifAvailable(ModelPersistenceService::restoreCustomModels);
        modelApplicationRunner.getObject().applyDemoModels();
        modelApplicationRunner.getObject().ensureSnmpLocalhostDevice();
        modelApplicationRunner.getObject().syncAllModelBackedVariableMetadata();
        initialized = true;
    }

    public ObjectTree tree() {
        return objectTree;
    }

    public PlatformObject require(String path) {
        return objectTree.require(path);
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
                nextSortOrder(parentPath)
        );
        objectTree.register(node);
        persistNode(node);
        publish(ObjectChangeEvent.of(ObjectChangeType.CREATED, fullPath));
        return node;
    }

    @Transactional
    public void reorderChildren(String parentPath, List<String> orderedPaths) {
        if (parentPath == null || parentPath.isBlank()) {
            throw new IllegalArgumentException("parentPath is required");
        }
        objectTree.require(parentPath);
        List<PlatformObject> children = objectTree.childrenOf(parentPath);
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
            persistNode(node);
        }
        publish(ObjectChangeEvent.of(ObjectChangeType.UPDATED, parentPath));
    }

    @Transactional
    public PlatformObject reconcileType(String path, ObjectType expectedType) {
        PlatformObject node = objectTree.require(path);
        if (node.type() != expectedType) {
            node.setType(expectedType);
            persistNode(node);
            publish(ObjectChangeEvent.of(ObjectChangeType.UPDATED, path));
        }
        return node;
    }

    @Transactional
    public PlatformObject updateInfo(String path, String displayName, String description) {
        PlatformObject node = objectTree.require(path);
        node.updateInfo(displayName, description);
        persistNode(node);
        publish(ObjectChangeEvent.of(ObjectChangeType.UPDATED, path));
        return node;
    }

    @Transactional
    public void delete(String path) {
        objectTree.delete(path);
        List<ObjectNodeEntity> toDelete = nodeRepository.findAllByOrderByPathAsc().stream()
                .filter(e -> e.getPath().equals(path) || e.getPath().startsWith(path + "."))
                .sorted(Comparator.comparingInt(e -> -e.getPath().length()))
                .toList();
        for (ObjectNodeEntity entity : toDelete) {
            variableRepository.deleteByObjectPath(entity.getPath());
            nodeRepository.deleteById(entity.getId());
        }
        publish(ObjectChangeEvent.of(ObjectChangeType.DELETED, path));
    }

    public Variable setDriverTelemetryValue(String path, String name, DataRecord value) {
        PlatformObject node = objectTree.require(path);
        Variable variable = node.getVariable(name).orElseGet(() -> {
            Variable created = new Variable(name, value.schema(), true, false, null, null);
            node.addVariable(created);
            return created;
        });
        variable.setComputedValue(value);
        persistVariable(path, variable);
        publish(ObjectChangeEvent.variableUpdated(path, name));
        return variable;
    }

    @Transactional
    public Variable setVariableValue(String path, String name, DataRecord value) {
        PlatformObject node = objectTree.require(path);
        node.setVariableValue(name, value);
        Variable variable = node.getVariable(name).orElseThrow();
        persistVariable(path, variable);
        publish(ObjectChangeEvent.variableUpdated(path, name));
        propagateBindings(path);
        return variable;
    }

    @Transactional
    public void deleteVariable(String path, String name) {
        PlatformObject node = objectTree.require(path);
        if (node.getVariable(name).isEmpty()) {
            return;
        }
        node.removeVariable(name);
        variableRepository.deleteByObjectPathAndName(path, name);
        publish(ObjectChangeEvent.variableUpdated(path, name));
    }

    @Transactional
    public Variable updateVariableHistory(
            String path,
            String name,
            boolean historyEnabled,
            Integer historyRetentionDays
    ) {
        PlatformObject node = objectTree.require(path);
        Variable variable = node.getVariable(name)
                .orElseThrow(() -> new IllegalArgumentException("Unknown variable: " + name));
        Variable updated = variable.withHistorySettings(historyEnabled, historyRetentionDays);
        node.addVariable(updated);
        persistVariable(path, updated);
        publish(ObjectChangeEvent.variableUpdated(path, name));
        return updated;
    }

    @Transactional
    public Variable setSystemVariableValue(String path, String name, DataRecord value) {
        PlatformObject node = objectTree.require(path);
        Variable variable = node.getVariable(name)
                .orElseThrow(() -> new IllegalArgumentException("Unknown variable: " + name));
        if (variable.writable()) {
            variable.setValue(value);
        } else {
            variable.setComputedValue(value);
        }
        persistVariable(path, variable);
        publish(ObjectChangeEvent.variableUpdated(path, name));
        return variable;
    }

    private void propagateBindings(String path) {
        PlatformObject node = objectTree.require(path);
        for (String changedName : bindingEvaluator.evaluateBindingsReturningChanges(node)) {
            Variable changed = node.getVariable(changedName).orElseThrow();
            persistVariable(path, changed);
            publish(ObjectChangeEvent.variableUpdated(path, changedName));
        }
    }

    @Transactional
    public void persistNodeTree(String path) {
        PlatformObject node = objectTree.require(path);
        persistNode(node);
        for (Variable variable : node.variables().values()) {
            persistVariable(path, variable);
        }
        publish(ObjectChangeEvent.of(ObjectChangeType.UPDATED, path));
    }

    private void ensureBootstrapNodes() {
        ensureBootstrapNode(
                "root.platform.operator-apps",
                ObjectType.OPERATOR_APPS,
                "Operator Apps",
                "Operator HMI — набор дашбордов для ?mode=operator&app=<id>",
                "app-folder-v1"
        );
        ensureBootstrapNode(
                "root.platform.alert-rules",
                ObjectType.ALERT_RULES,
                "Alert Rules",
                "CEL rules that publish events on variable changes",
                null
        );
        ensureBootstrapNode(
                "root.platform.correlators",
                ObjectType.CORRELATORS,
                "Correlators",
                "Event patterns that trigger workflows",
                null
        );
        ensureBootstrapNode(
                "root.platform.federation",
                ObjectType.AGENT,
                "Federation",
                "Remote ISPF sites — peer registry and cross-site object proxy (PF-13 spike)",
                null
        );
        ensureBootstrapNode(
                "root.tenant",
                ObjectType.TENANT,
                "Tenants",
                "Multi-tenant namespaces (root.tenant.{id}.platform.*)",
                null
        );
    }

    private void ensureBootstrapNode(
            String path,
            ObjectType type,
            String displayName,
            String description,
            String templateId
    ) {
        if (objectTree.findByPath(path).isPresent()) {
            return;
        }
        int lastDot = path.lastIndexOf('.');
        String parentPath = path.substring(0, lastDot);
        String name = path.substring(lastDot + 1);
        create(parentPath, name, type, displayName, description, templateId);
    }

    private void seedPlatformStructure() {
        platformBootstrap.initialize(objectTree);
        persistAll();
    }

    private void seedPlatform() {
        seedPlatformStructure();
        modelApplicationRunner.getObject().applyDemoModels();
    }

    private void persistAll() {
        for (PlatformObject node : objectTree.all()) {
            persistNode(node);
            for (Variable variable : node.variables().values()) {
                persistVariable(node.path(), variable);
            }
        }
    }

    private void loadFromDatabase() {
        List<ObjectNodeEntity> nodes = nodeRepository.findAllByOrderByPathAsc();
        for (ObjectNodeEntity entity : nodes) {
            if ("root".equals(entity.getPath())) {
                continue;
            }
            PlatformObject node = new PlatformObject(
                    entity.getId(),
                    entity.getPath(),
                    entity.getType(),
                    entity.getDisplayName(),
                    entity.getDescription(),
                    entity.getTemplateId(),
                    entity.getSortOrder()
            );
            for (EventDescriptor event : mapper.readEvents(entity.getEventsJson())) {
                node.addEvent(event);
            }
            for (FunctionDescriptor function : mapper.readFunctions(entity.getFunctionsJson())) {
                node.addFunction(function);
            }
            objectTree.register(node);
        }
        for (ObjectNodeEntity entity : nodes) {
            if ("root".equals(entity.getPath())) {
                continue;
            }
            PlatformObject node = objectTree.require(entity.getPath());
            for (ObjectVariableEntity varEntity : variableRepository.findByObjectPath(entity.getPath())) {
                DataRecord value = mapper.readDataRecord(varEntity.getValueJson());
                node.addVariable(new Variable(
                        varEntity.getName(),
                        mapper.readSchema(varEntity.getSchemaJson()),
                        varEntity.isReadable(),
                        varEntity.isWritable(),
                        varEntity.getBindingExpr(),
                        value,
                        varEntity.isHistoryEnabled(),
                        varEntity.getHistoryRetentionDays()
                ));
            }
        }
    }

    private void persistNode(PlatformObject node) {
        nodeRepository.save(mapper.toEntity(node));
    }

    private int nextSortOrder(String parentPath) {
        return objectTree.childrenOf(parentPath).stream()
                .mapToInt(PlatformObject::sortOrder)
                .max()
                .orElse(-1) + 1;
    }

    private void persistVariable(String path, Variable variable) {
        ObjectVariableEntity entity = variableRepository
                .findByObjectPathAndName(path, variable.name())
                .orElseGet(ObjectVariableEntity::new);
        ObjectVariableEntity mapped = mapper.toEntity(path, variable);
        entity.setObjectPath(path);
        entity.setName(variable.name());
        entity.setSchemaJson(mapped.getSchemaJson());
        entity.setValueJson(mapped.getValueJson());
        entity.setReadable(mapped.isReadable());
        entity.setWritable(mapped.isWritable());
        entity.setBindingExpr(mapped.getBindingExpr());
        entity.setUpdatedAt(mapped.getUpdatedAt());
        entity.setHistoryEnabled(mapped.isHistoryEnabled());
        entity.setHistoryRetentionDays(mapped.getHistoryRetentionDays());
        variableRepository.save(entity);
    }

    private void publish(ObjectChangeEvent event) {
        eventPublisher.publishEvent(event);
    }
}
