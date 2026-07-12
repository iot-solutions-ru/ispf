package com.ispf.server.object;

import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.HistorySampleMode;
import com.ispf.core.object.Variable;
import com.ispf.core.object.VariableStorageMode;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.server.application.reference.mes.MesBlueprintBootstrap;
import com.ispf.server.bootstrap.DemoFixtureBootstrap;
import com.ispf.server.bootstrap.PlatformBootstrap;
import com.ispf.server.bootstrap.PlatformCatalogSortOrder;
import com.ispf.server.bootstrap.PlatformReferenceBlueprintBootstrap;
import com.ispf.server.bootstrap.SystemObjectDescriptions;
import com.ispf.server.config.BootstrapProperties;
import com.ispf.server.config.MqttGatewayProperties;
import com.ispf.server.driver.DeviceTelemetryPolicyService;
import com.ispf.server.driver.TelemetryPublishMode;
import com.ispf.server.federation.FederationPaths;
import com.ispf.server.function.java.JavaFunctionRuntimeService;
import com.ispf.server.persistence.ObjectEntityMapper;
import com.ispf.server.persistence.ObjectNodeRepository;
import com.ispf.server.persistence.ObjectVariableRepository;
import com.ispf.server.persistence.entity.ObjectNodeEntity;
import com.ispf.server.persistence.entity.ObjectVariableEntity;
import com.ispf.plugin.blueprint.BlueprintCatalogRoots;
import com.ispf.plugin.blueprint.BlueprintEngine;
import com.ispf.server.plugin.blueprint.BlueprintApplicationRunner;
import com.ispf.server.plugin.blueprint.BlueprintBootstrap;
import com.ispf.server.plugin.blueprint.BlueprintPersistenceService;
import com.ispf.server.plugin.blueprint.SystemIntrinsicBlueprintMigration;
import com.ispf.server.history.TelemetryHistorianFastPath;
import com.ispf.server.event.TelemetryEventJournalFastPath;
import com.ispf.server.function.MqttGatewayIngressDispatchService;
import com.ispf.server.object.pubsub.ObjectChangePublicationService;
import com.ispf.server.platform.ClusterPlatformBootstrapService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
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
    private final BootstrapProperties bootstrapProperties;
    private final ObjectProvider<BlueprintBootstrap> blueprintBootstrap;
    private final ObjectProvider<BlueprintApplicationRunner> blueprintApplicationRunner;
    private final ObjectProvider<BlueprintPersistenceService> blueprintPersistence;
    private final ObjectProvider<BlueprintEngine> blueprintEngine;
    private final ObjectProvider<SystemIntrinsicBlueprintMigration> intrinsicBlueprintMigration;
    private final ObjectProvider<DemoFixtureBootstrap> demoFixtureBootstrap;
    private final ObjectProvider<PlatformReferenceBlueprintBootstrap> platformReferenceBlueprintBootstrap;
    private final ObjectProvider<MesBlueprintBootstrap> mesBlueprintBootstrap;
    private final ObjectProvider<VisualGroupService> visualGroupService;
    private final ObjectChangePublicationService publicationService;
    private final ObjectProvider<ObjectManager> self;
    private final ObjectConfigAuditService configAuditService;
    private final RuntimeTelemetryCoalescer telemetryCoalescer;
    private final TelemetryIngressDispatcher telemetryIngressDispatcher;
    private final MqttGatewayIngressDispatchService gatewayIngressDispatch;
    private final MqttGatewayProperties mqttGatewayProperties;
    private final TelemetryHistorianFastPath historianFastPath;
    private final TelemetryEventJournalFastPath eventJournalFastPath;
    private final DeviceTelemetryPolicyService telemetryPolicyService;
    private final JavaFunctionRuntimeService javaFunctionRuntimeService;
    private final ObjectProvider<ClusterPlatformBootstrapService> clusterBootstrapService;
    private final ConcurrentHashMap<String, Object> variablePersistLocks = new ConcurrentHashMap<>();
    private volatile boolean initialized;

    public ObjectManager(
            ObjectNodeRepository nodeRepository,
            ObjectVariableRepository variableRepository,
            ObjectEntityMapper mapper,
            PlatformBootstrap platformBootstrap,
            BootstrapProperties bootstrapProperties,
            ObjectProvider<BlueprintBootstrap> blueprintBootstrap,
            ObjectProvider<BlueprintApplicationRunner> blueprintApplicationRunner,
            ObjectProvider<BlueprintPersistenceService> blueprintPersistence,
            ObjectProvider<BlueprintEngine> blueprintEngine,
            ObjectProvider<SystemIntrinsicBlueprintMigration> intrinsicBlueprintMigration,
            ObjectProvider<DemoFixtureBootstrap> demoFixtureBootstrap,
            ObjectProvider<PlatformReferenceBlueprintBootstrap> platformReferenceBlueprintBootstrap,
            ObjectProvider<MesBlueprintBootstrap> mesBlueprintBootstrap,
            ObjectProvider<VisualGroupService> visualGroupService,
            @org.springframework.context.annotation.Lazy ObjectChangePublicationService publicationService,
            ObjectProvider<ObjectManager> self,
            ObjectConfigAuditService configAuditService,
            @org.springframework.context.annotation.Lazy RuntimeTelemetryCoalescer telemetryCoalescer,
            @org.springframework.context.annotation.Lazy TelemetryIngressDispatcher telemetryIngressDispatcher,
            @org.springframework.context.annotation.Lazy MqttGatewayIngressDispatchService gatewayIngressDispatch,
            @org.springframework.context.annotation.Lazy MqttGatewayProperties mqttGatewayProperties,
            @org.springframework.context.annotation.Lazy TelemetryHistorianFastPath historianFastPath,
            @org.springframework.context.annotation.Lazy TelemetryEventJournalFastPath eventJournalFastPath,
            @org.springframework.context.annotation.Lazy DeviceTelemetryPolicyService telemetryPolicyService,
            JavaFunctionRuntimeService javaFunctionRuntimeService,
            ObjectProvider<ClusterPlatformBootstrapService> clusterBootstrapService
    ) {
        this.nodeRepository = nodeRepository;
        this.variableRepository = variableRepository;
        this.mapper = mapper;
        this.platformBootstrap = platformBootstrap;
        this.bootstrapProperties = bootstrapProperties;
        this.blueprintBootstrap = blueprintBootstrap;
        this.blueprintApplicationRunner = blueprintApplicationRunner;
        this.blueprintPersistence = blueprintPersistence;
        this.blueprintEngine = blueprintEngine;
        this.intrinsicBlueprintMigration = intrinsicBlueprintMigration;
        this.demoFixtureBootstrap = demoFixtureBootstrap;
        this.platformReferenceBlueprintBootstrap = platformReferenceBlueprintBootstrap;
        this.mesBlueprintBootstrap = mesBlueprintBootstrap;
        this.visualGroupService = visualGroupService;
        this.publicationService = publicationService;
        this.self = self;
        this.configAuditService = configAuditService;
        this.telemetryCoalescer = telemetryCoalescer;
        this.telemetryIngressDispatcher = telemetryIngressDispatcher;
        this.gatewayIngressDispatch = gatewayIngressDispatch;
        this.mqttGatewayProperties = mqttGatewayProperties;
        this.historianFastPath = historianFastPath;
        this.eventJournalFastPath = eventJournalFastPath;
        this.telemetryPolicyService = telemetryPolicyService;
        this.javaFunctionRuntimeService = javaFunctionRuntimeService;
        this.clusterBootstrapService = clusterBootstrapService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Transactional
    public synchronized void initialize() {
        if (initialized) {
            return;
        }
        clusterBootstrapService.ifAvailable(ClusterPlatformBootstrapService::prepareFixtureBootstrapRole);
        if (nodeRepository.existsByPath("root.platform")) {
            loadFromDatabase();
            ensureBootstrapNodes();
        } else {
            seedPlatformStructure();
        }
        blueprintBootstrap.getObject().ensureBuiltInBlueprints();
        blueprintPersistence.ifAvailable(BlueprintPersistenceService::restoreCustomBlueprints);
        intrinsicBlueprintMigration.ifAvailable(SystemIntrinsicBlueprintMigration::migrate);
        platformReferenceBlueprintBootstrap.ifAvailable(PlatformReferenceBlueprintBootstrap::ensureReferenceModels);
        mesBlueprintBootstrap.ifAvailable(MesBlueprintBootstrap::ensureMesModels);
        if (shouldApplyFixtureBlueprints()) {
            demoFixtureBootstrap.ifAvailable(demo ->
                    demo.seedDemos(blueprintApplicationRunner.getObject()));
        }
        blueprintEngine.ifAvailable(engine -> {
            engine.refreshBlueprintCatalogNodes();
            cleanupLegacyBlueprintCatalog();
        });
        blueprintApplicationRunner.getObject().syncAllBlueprintBackedVariableMetadata();
        blueprintApplicationRunner.getObject().restoreAttachments();
        blueprintApplicationRunner.getObject().ensureDashboardDemoRules();
    }

    public boolean isInitialized() {
        return initialized;
    }

    public synchronized void markInitialized() {
        initialized = true;
    }

    public ObjectTree tree() {
        return objectTree;
    }

    public PlatformObject require(String path) {
        Optional<PlatformObject> found = objectTree.findByPath(path);
        ClusterPlatformBootstrapService cluster = clusterBootstrapService.getIfAvailable();
        if (found.isPresent()) {
            if (isInitialized() && cluster != null && cluster.isClusterActive()
                    && nodeRepository.findByPath(path).isEmpty()) {
                removePathFromMemoryIfPresent(path);
                throw new ObjectNotFoundException(path);
            }
            return found.get();
        }
        if (cluster != null && cluster.isClusterActive()) {
            reloadPathFromDatabase(path);
        }
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
                PlatformCatalogSortOrder.forPath(fullPath).orElseGet(() -> nextSortOrder(parentPath))
        );
        objectTree.register(node);
        node.setRevision(1L);
        node.setLastChangedBy(ObjectRevisionContext.actor());
        node.setLastChangedAt(Instant.now());
        persistNode(node);
        publish(ObjectChangeEvent.of(ObjectChangeType.CREATED, fullPath, node.revision(), node.lastChangedBy()));
        return node;
    }

    @Transactional
    public void reorderChildren(String parentPath, List<String> orderedPaths) {
        if (parentPath == null || parentPath.isBlank()) {
            throw new IllegalArgumentException("parentPath is required");
        }
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
            persistNode(node);
        }
        publish(ObjectChangeEvent.of(ObjectChangeType.UPDATED, parentPath));
    }

    /** Direct children visible in the structural tree (excludes visual-group members). */
    List<PlatformObject> structuralChildrenOf(String parentPath) {
        VisualGroupService groups = visualGroupService.getIfAvailable();
        return objectTree.childrenOf(parentPath).stream()
                .filter(node -> groups == null || !groups.isHiddenFromStructuralTree(node.path()))
                .toList();
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
    public void ensureSystemCatalogFolder(String path, ObjectType type, String templateId) {
        SystemObjectDescriptions.Entry entry = SystemObjectDescriptions.resolve(path)
                .orElseThrow(() -> new IllegalStateException("Missing system description: " + path));
        if (objectTree.findByPath(path).isPresent()) {
            self.getObject().updateInfo(path, entry.displayName(), entry.description());
            self.getObject().reconcileType(path, type);
            return;
        }
        Optional<ObjectNodeEntity> persisted = nodeRepository.findByPath(path);
        if (persisted.isPresent()) {
            attachPersistedNode(persisted.get());
            updateInfo(path, entry.displayName(), entry.description());
            reconcileType(path, type);
            return;
        }
        int lastDot = path.lastIndexOf('.');
        self.getObject().create(
                path.substring(0, lastDot),
                path.substring(lastDot + 1),
                type,
                entry.displayName(),
                entry.description(),
                templateId
        );
    }

    private void attachPersistedNode(ObjectNodeEntity entity) {
        if (objectTree.findByPath(entity.getPath()).isPresent()) {
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
        objectTree.register(node);
        for (ObjectVariableEntity varEntity : variableRepository.findByObjectPathIn(List.of(entity.getPath()))) {
            node.addVariable(mapper.toVariable(varEntity));
        }
    }

    @Transactional
    public PlatformObject updateInfo(String path, String displayName, String description) {
        assertExpectedRevision(path);
        PlatformObject node = objectTree.require(path);
        long revisionBefore = node.revision();
        Map<String, String> before = metadataSnapshot(node);
        node.updateInfo(displayName, description);
        persistNodeConfig(node, "UPDATE_INFO", "metadata", mapper.auditDiff(before, metadataSnapshot(node)));
        publishConfigChange(ObjectChangeType.UPDATED, path, revisionBefore);
        return node;
    }

    @Transactional
    public PlatformObject updateBindingAuditEnabled(String path, boolean enabled) {
        assertExpectedRevision(path);
        PlatformObject node = objectTree.require(path);
        long revisionBefore = node.revision();
        boolean before = node.bindingAuditEnabled();
        node.setBindingAuditEnabled(enabled);
        persistNodeConfig(
                node,
                "UPDATE_BINDING_AUDIT",
                "bindingAuditEnabled",
                mapper.auditDiff(before, enabled)
        );
        publishConfigChange(ObjectChangeType.UPDATED, path, revisionBefore);
        return node;
    }

    public boolean isBindingAuditEnabled(String path) {
        return objectTree.findByPath(path).map(PlatformObject::bindingAuditEnabled).orElse(false);
    }

    @Transactional
    public PlatformObject updateFunctionAuditEnabled(String path, boolean enabled) {
        assertExpectedRevision(path);
        PlatformObject node = objectTree.require(path);
        long revisionBefore = node.revision();
        boolean before = node.functionAuditEnabled();
        node.setFunctionAuditEnabled(enabled);
        persistNodeConfig(
                node,
                "UPDATE_FUNCTION_AUDIT",
                "functionAuditEnabled",
                mapper.auditDiff(before, enabled)
        );
        publishConfigChange(ObjectChangeType.UPDATED, path, revisionBefore);
        return node;
    }

    public boolean isFunctionAuditEnabled(String path) {
        return objectTree.findByPath(path).map(PlatformObject::functionAuditEnabled).orElse(false);
    }

    @Transactional
    public PlatformObject updateEventJournalEnabled(String path, boolean enabled) {
        assertExpectedRevision(path);
        PlatformObject node = objectTree.require(path);
        long revisionBefore = node.revision();
        boolean before = node.eventJournalEnabled();
        node.setEventJournalEnabled(enabled);
        persistNodeConfig(
                node,
                "UPDATE_EVENT_JOURNAL",
                "eventJournalEnabled",
                mapper.auditDiff(before, enabled)
        );
        publishConfigChange(ObjectChangeType.UPDATED, path, revisionBefore);
        return node;
    }

    public boolean isEventJournalEnabled(String path) {
        return objectTree.findByPath(path).map(PlatformObject::eventJournalEnabled).orElse(false);
    }

    @Transactional
    public void delete(String path) {
        if (path == null || path.isBlank() || "root".equals(path)) {
            throw new IllegalArgumentException("Cannot delete root object");
        }
        List<ObjectNodeEntity> toDelete = nodeRepository.findAllByOrderByPathAsc().stream()
                .filter(e -> e.getPath().equals(path) || e.getPath().startsWith(path + "."))
                .sorted(Comparator.comparingInt(e -> -e.getPath().length()))
                .toList();
        if (toDelete.isEmpty() && objectTree.findByPath(path).isEmpty()) {
            throw new ObjectNotFoundException(path);
        }
        removePathFromMemoryIfPresent(path);
        for (ObjectNodeEntity entity : toDelete) {
            variableRepository.deleteByObjectPath(entity.getPath());
            nodeRepository.deleteById(entity.getId());
        }
        publish(ObjectChangeEvent.of(ObjectChangeType.DELETED, path));
    }

    public Variable setDriverTelemetryValue(String path, String name, DataRecord value) {
        return setDriverTelemetryValue(path, name, value, null);
    }

    public Variable setDriverTelemetryValue(String path, String name, DataRecord value, Instant observedAt) {
        if (eventJournalFastPath.isEligible(path, name)
                && eventJournalFastPath.tryFire(path, name, value, observedAt)) {
            return resolveDriverTelemetryVariable(path, name, value);
        }
        if (historianFastPath.isHistorianOnlyEligible(path, name)
                && historianFastPath.tryPublish(path, name, value, observedAt)) {
            return resolveDriverTelemetryVariable(path, name, value);
        }
        Variable variable = setDriverTelemetryValueInMemory(path, name, value);
        if (gatewayIngressDispatch.tryScheduleDispatch(path, name, value)) {
            return variable;
        }
        if (telemetryIngressDispatcher.isQueueEnabled()) {
            telemetryIngressDispatcher.submit(path, name, value, observedAt);
        } else {
            telemetryCoalescer.recordUpdate(path, name, value, observedAt);
        }
        return variable;
    }

    /**
     * Fast-path ingress (event journal / historian-only): skip RAM live-value update — downstream
     * async stores do not need object-tree computed value per tick.
     */
    private Variable resolveDriverTelemetryVariable(String path, String name, DataRecord value) {
        PlatformObject node = objectTree.require(path);
        return node.getVariable(name).orElseGet(() -> {
            Variable created = new Variable(name, value.schema(), true, false, null);
            node.addVariable(created);
            return created;
        });
    }

    public Variable setDriverTelemetryValueDirect(String path, String name, DataRecord value) {
        return setDriverTelemetryValueInMemory(path, name, value);
    }

    private Variable setDriverTelemetryValueInMemory(String path, String name, DataRecord value) {
        PlatformObject node = require(path);
        Variable variable = node.getVariable(name).orElseGet(() -> {
            Variable created = new Variable(name, value.schema(), true, false, null);
            node.addVariable(created);
            return created;
        });
        variable.setComputedValue(value);
        return variable;
    }

    /** In-memory variable update for high-frequency driver/runtime data (no DB flush per tick). */
    public Variable setRuntimeVariableValue(String path, String name, DataRecord value, boolean publishEvent) {
        PlatformObject node = objectTree.require(path);
        Variable variable = node.getVariable(name).orElseGet(() -> {
            Variable created = new Variable(name, value.schema(), true, false, null);
            node.addVariable(created);
            return created;
        });
        variable.setComputedValue(value);
        if (publishEvent) {
            publish(ObjectChangeEvent.variableUpdated(path, name));
        }
        return variable;
    }

    @Transactional
    public Variable setVariableValue(String path, String name, DataRecord value) {
        return setVariableValue(path, name, value, null);
    }

    @Transactional
    public Variable setVariableValue(String path, String name, DataRecord value, Instant observedAt) {
        assertUserVariable(name);
        assertExpectedRevision(path);
        PlatformObject node = objectTree.require(path);
        long revisionBefore = node.revision();
        DataRecord before = node.getVariable(name).flatMap(Variable::value).orElse(null);
        node.setVariableValue(name, value);
        Variable variable = node.getVariable(name).orElseThrow();
        persistVariable(path, variable);
        bumpRevision(node);
        recordAudit(
                path,
                "SET_VARIABLE_VALUE",
                name,
                revisionBefore,
                node.revision(),
                mapper.auditDiff(before, value)
        );
        publishConfigChange(
                ObjectChangeEvent.variableUpdated(
                        path,
                        name,
                        false,
                        true,
                        observedAt,
                        value,
                        variable.includePreviousValueInEvent() ? before : null
                ),
                node
        );
        return variable;
    }

    @Transactional
    public void deleteVariable(String path, String name) {
        assertUserVariable(name);
        assertExpectedRevision(path);
        PlatformObject node = objectTree.require(path);
        Optional<Variable> existing = node.getVariable(name);
        if (existing.isEmpty()) {
            return;
        }
        long revisionBefore = node.revision();
        Map<String, Object> before = variableSnapshot(existing.get());
        node.removeVariable(name);
        variableRepository.deleteByObjectPathAndName(path, name);
        bumpRevision(node);
        recordAudit(
                path,
                "DELETE_VARIABLE",
                name,
                revisionBefore,
                node.revision(),
                mapper.auditDiff(before, null)
        );
        publishConfigChange(ObjectChangeEvent.variableUpdated(path, name), node);
    }

    @Transactional
    public Variable createVariable(
            String path,
            String name,
            DataSchema schema,
            boolean readable,
            boolean writable,
            DataRecord initialValue,
            boolean historyEnabled,
            Integer historyRetentionDays
    ) {
        return createVariable(
                path,
                name,
                schema,
                readable,
                writable,
                initialValue,
                historyEnabled,
                historyRetentionDays,
                List.of(),
                List.of()
        );
    }

    @Transactional
    public Variable createVariable(
            String path,
            String name,
            DataSchema schema,
            boolean readable,
            boolean writable,
            DataRecord initialValue,
            boolean historyEnabled,
            Integer historyRetentionDays,
            List<String> readRoles,
            List<String> writeRoles
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Variable name is required");
        }
        if (ObjectUiIconService.UI_ICON_VARIABLE.equals(name) || BindingStateVariables.isReserved(name)) {
            throw new IllegalArgumentException("Reserved variable name: " + name);
        }
        PlatformObject node = objectTree.require(path);
        if (node.getVariable(name).isPresent()) {
            throw new IllegalArgumentException("Variable already exists: " + name);
        }
        assertExpectedRevision(path);
        long revisionBefore = node.revision();
        Variable variable = new Variable(
                name,
                schema,
                readable,
                writable,
                initialValue,
                historyEnabled,
                historyRetentionDays,
                readRoles,
                writeRoles
        );
        node.addVariable(variable);
        persistVariable(path, variable);
        bumpRevision(node);
        recordAudit(
                path,
                "CREATE_VARIABLE",
                name,
                revisionBefore,
                node.revision(),
                mapper.auditDiff(null, variableSnapshot(variable))
        );
        publishConfigChange(ObjectChangeEvent.variableUpdated(path, name), node);
        return node.getVariable(name).orElseThrow();
    }

    @Transactional
    public Variable updateVariableDefinition(
            String path,
            String name,
            Boolean readable,
            Boolean writable
    ) {
        return updateVariableDefinition(path, name, readable, writable, null, null);
    }

    @Transactional
    public Variable updateVariableDefinition(
            String path,
            String name,
            Boolean readable,
            Boolean writable,
            List<String> readRoles,
            List<String> writeRoles
    ) {
        assertExpectedRevision(path);
        PlatformObject node = objectTree.require(path);
        long revisionBefore = node.revision();
        Variable variable = node.getVariable(name)
                .orElseThrow(() -> new IllegalArgumentException("Unknown variable: " + name));
        if (ObjectUiIconService.UI_ICON_VARIABLE.equals(name) || BindingStateVariables.isReserved(name)) {
            throw new IllegalArgumentException("Cannot modify reserved variable: " + name);
        }
        Map<String, Object> before = variableDefinitionSnapshot(variable);
        boolean nextReadable = readable != null ? readable : variable.readable();
        boolean nextWritable = writable != null ? writable : variable.writable();
        List<String> nextReadRoles = readRoles != null ? readRoles : variable.readRoles();
        List<String> nextWriteRoles = writeRoles != null ? writeRoles : variable.writeRoles();
        Variable updated = variable.withDefinition(
                nextReadable,
                nextWritable,
                variable.historyEnabled(),
                variable.historyRetentionDays().orElse(null),
                nextReadRoles,
                nextWriteRoles
        );
        node.addVariable(updated);
        persistVariable(path, updated);
        bumpRevision(node);
        recordAudit(
                path,
                "UPDATE_VARIABLE",
                name,
                revisionBefore,
                node.revision(),
                mapper.auditDiff(before, variableDefinitionSnapshot(updated))
        );
        publishConfigChange(ObjectChangeEvent.variableUpdated(path, name), node);
        return updated;
    }

    @Transactional
    public FunctionDescriptor upsertFunction(String path, FunctionDescriptor function) {
        if (function == null || function.name() == null || function.name().isBlank()) {
            throw new IllegalArgumentException("Function name is required");
        }
        assertExpectedRevision(path);
        PlatformObject node = objectTree.require(path);
        long revisionBefore = node.revision();
        FunctionDescriptor before = node.functions().get(function.name());
        javaFunctionRuntimeService.syncOnSave(path, function, before);
        node.addFunction(function);
        persistNodeConfig(
                node,
                "UPSERT_FUNCTION",
                function.name(),
                mapper.auditDiff(before, function)
        );
        publishConfigChange(ObjectChangeType.UPDATED, path, revisionBefore);
        return function;
    }

    @Transactional
    public void deleteFunction(String path, String name) {
        assertExpectedRevision(path);
        PlatformObject node = objectTree.require(path);
        FunctionDescriptor before = node.functions().get(name);
        if (before == null) {
            return;
        }
        long revisionBefore = node.revision();
        node.removeFunction(name);
        javaFunctionRuntimeService.unregister(path, name);
        persistNodeConfig(node, "DELETE_FUNCTION", name, mapper.auditDiff(before, null));
        publishConfigChange(ObjectChangeType.UPDATED, path, revisionBefore);
    }

    @Transactional
    public EventDescriptor upsertEvent(String path, EventDescriptor event) {
        if (event == null || event.name() == null || event.name().isBlank()) {
            throw new IllegalArgumentException("Event name is required");
        }
        assertExpectedRevision(path);
        PlatformObject node = objectTree.require(path);
        long revisionBefore = node.revision();
        EventDescriptor before = node.events().get(event.name());
        node.addEvent(event);
        persistNodeConfig(node, "UPSERT_EVENT", event.name(), mapper.auditDiff(before, event));
        publishConfigChange(ObjectChangeType.UPDATED, path, revisionBefore);
        return event;
    }

    @Transactional
    public void deleteEvent(String path, String name) {
        assertExpectedRevision(path);
        PlatformObject node = objectTree.require(path);
        EventDescriptor before = node.events().get(name);
        if (before == null) {
            return;
        }
        long revisionBefore = node.revision();
        node.removeEvent(name);
        persistNodeConfig(node, "DELETE_EVENT", name, mapper.auditDiff(before, null));
        publishConfigChange(ObjectChangeType.UPDATED, path, revisionBefore);
    }

    @Transactional
    public Variable updateVariableHistory(
            String path,
            String name,
            boolean historyEnabled,
            Integer historyRetentionDays,
            String telemetryPublishMode
    ) {
        return updateVariableHistory(
                path,
                name,
                historyEnabled,
                historyRetentionDays,
                telemetryPublishMode,
                null,
                null,
                null
        );
    }

    @Transactional
    public Variable updateVariableHistory(
            String path,
            String name,
            boolean historyEnabled,
            Integer historyRetentionDays,
            String telemetryPublishMode,
            HistorySampleMode historySampleMode,
            Boolean includePreviousValueInEvent,
            VariableStorageMode storageMode
    ) {
        TelemetryPublishMode.validateOverride(telemetryPublishMode);
        assertExpectedRevision(path);
        PlatformObject node = objectTree.require(path);
        long revisionBefore = node.revision();
        Variable variable = node.getVariable(name)
                .orElseThrow(() -> new IllegalArgumentException("Unknown variable: " + name));
        Map<String, Object> before = variableHistorySnapshot(variable);
        Variable updated = variable.withPolicySettings(
                historyEnabled,
                historyRetentionDays,
                historySampleMode != null ? historySampleMode : variable.historySampleMode(),
                includePreviousValueInEvent != null
                        ? includePreviousValueInEvent
                        : variable.includePreviousValueInEvent(),
                storageMode != null ? storageMode : variable.storageMode(),
                telemetryPublishMode
        );
        node.addVariable(updated);
        persistVariable(path, updated);
        bumpRevision(node);
        telemetryPolicyService.invalidateVariable(path, name);
        recordAudit(
                path,
                "UPDATE_VARIABLE_HISTORY",
                name,
                revisionBefore,
                node.revision(),
                mapper.auditDiff(before, variableHistorySnapshot(updated))
        );
        publishConfigChange(ObjectChangeEvent.variableUpdated(path, name), node);
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

    @Transactional
    public Variable upsertSystemVariable(
            String path,
            String name,
            DataSchema schema,
            DataRecord value
    ) {
        synchronized (lockForVariable(path, name)) {
            PlatformObject node = objectTree.require(path);
            if (node.getVariable(name).isPresent()) {
                return setSystemVariableValue(path, name, value);
            }
            Optional<ObjectVariableEntity> persisted = variableRepository.findByObjectPathAndName(path, name);
            if (persisted.isPresent()) {
                ObjectVariableEntity entity = persisted.get();
                node.addVariable(mapper.toVariable(entity));
                return setSystemVariableValue(path, name, value);
            }
            Variable created = new Variable(name, schema, false, false, value);
            node.addVariable(created);
            try {
                persistVariable(path, created);
            } catch (DataIntegrityViolationException duplicate) {
                node.removeVariable(name);
                Optional<ObjectVariableEntity> raced = variableRepository.findByObjectPathAndName(path, name);
                if (raced.isEmpty()) {
                    throw duplicate;
                }
                ObjectVariableEntity entity = raced.get();
                node.addVariable(mapper.toVariable(entity));
                return setSystemVariableValue(path, name, value);
            }
            publish(ObjectChangeEvent.variableUpdated(path, name));
            return created;
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
        ensureBootstrapNode("root.platform.operator-apps", ObjectType.OPERATOR_APPS, "app-folder-v1");
        ensureBootstrapNode("root.platform.alert-rules", ObjectType.ALERT_RULES, null);
        ensureBootstrapNode("root.platform.correlators", ObjectType.CORRELATORS, null);
        ensureBootstrapNode("root.platform.queries", ObjectType.QUERIES, null);
        ensureBootstrapNode("root.platform.event-filters", ObjectType.EVENT_FILTERS, null);
        ensureBootstrapNode("root.platform.event-frames", ObjectType.EVENT_FRAMES, null);
        ensureBootstrapNode("root.platform.mes", ObjectType.MES, null);
        ensureBootstrapNode(FederationPaths.FEDERATION_ROOT, ObjectType.AGENT, null);
        ensureBootstrapNode("root.tenant", ObjectType.TENANT, null);
    }

    private void ensureBootstrapNode(String path, ObjectType type, String templateId) {
        SystemObjectDescriptions.Entry entry = SystemObjectDescriptions.resolve(path)
                .orElseThrow(() -> new IllegalStateException("Missing system description: " + path));
        if (objectTree.findByPath(path).isPresent()) {
            updateInfo(path, entry.displayName(), entry.description());
            reconcileType(path, type);
            return;
        }
        Optional<ObjectNodeEntity> persisted = nodeRepository.findByPath(path);
        if (persisted.isPresent()) {
            registerNodeFromEntity(
                    persisted.get(),
                    loadVariablesByPath(List.of(path)).getOrDefault(path, List.of())
            );
            updateInfo(path, entry.displayName(), entry.description());
            reconcileType(path, type);
            return;
        }
        int lastDot = path.lastIndexOf('.');
        String parentPath = path.substring(0, lastDot);
        String name = path.substring(lastDot + 1);
        create(parentPath, name, type, entry.displayName(), entry.description(), templateId);
    }

    private void seedPlatformStructure() {
        platformBootstrap.initialize(objectTree);
        persistAll();
    }

    private void persistAll() {
        for (PlatformObject node : objectTree.all()) {
            persistNode(node);
            for (Variable variable : node.variables().values()) {
                persistVariable(node.path(), variable);
            }
        }
    }

    public synchronized void reloadFromDatabase() {
        clearNonRootNodes();
        loadFromDatabase();
        ensureBootstrapNodes();
    }

    public synchronized void syncPathFromDatabase(String path) {
        reloadPathFromDatabase(path);
    }

    /**
     * Reloads node metadata and all persisted variables from PostgreSQL (cluster follower sync).
     * Removes RAM variables absent in PG; drops the path from RAM when the node no longer exists.
     */
    public synchronized void reloadPathFromDatabase(String path) {
        if (path == null || path.isBlank() || "root".equals(path)) {
            return;
        }
        Optional<ObjectNodeEntity> entityOpt = nodeRepository.findByPath(path);
        if (entityOpt.isEmpty()) {
            removePathFromMemoryIfPresent(path);
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

    /** Reloads a persisted config variable from PostgreSQL (cluster follower sync). */
    public synchronized void syncVariableFromDatabase(String path, String name) {
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

    public synchronized void removePathFromMemoryIfPresent(String path) {
        if (path == null || path.isBlank() || "root".equals(path)) {
            return;
        }
        if (objectTree.findByPath(path).isPresent()) {
            objectTree.delete(path);
        }
    }

    private boolean shouldApplyFixtureBlueprints() {
        if (!bootstrapProperties.shouldSeedGeneralReferenceDemos()) {
            return false;
        }
        ClusterPlatformBootstrapService bootstrap = clusterBootstrapService.getIfAvailable();
        return bootstrap == null || bootstrap.shouldRunFixtureBootstrap();
    }

    private void clearNonRootNodes() {
        List<String> paths = objectTree.all().stream()
                .map(PlatformObject::path)
                .filter(path -> !"root".equals(path))
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        for (String path : paths) {
            objectTree.delete(path);
        }
    }

    private void loadFromDatabase() {
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

    private void registerNodeFromEntity(ObjectNodeEntity entity, List<ObjectVariableEntity> variableEntities) {
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

    private Map<String, List<ObjectVariableEntity>> loadVariablesByPath(List<String> nodePaths) {
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

    private void cleanupLegacyBlueprintCatalog() {
        String legacyPrefix = BlueprintCatalogRoots.LEGACY + ".";
        List<String> legacyPaths = objectTree.all().stream()
                .map(PlatformObject::path)
                .filter(path -> path.equals(BlueprintCatalogRoots.LEGACY) || path.startsWith(legacyPrefix))
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        for (String path : legacyPaths) {
            if (objectTree.findByPath(path).isEmpty()) {
                continue;
            }
            objectTree.delete(path);
            nodeRepository.findByPath(path).ifPresent(entity -> {
                variableRepository.deleteByObjectPath(path);
                nodeRepository.deleteById(entity.getId());
            });
        }
    }

    private void persistNode(PlatformObject node) {
        ObjectNodeEntity mapped = mapper.toEntity(node);
        nodeRepository.findByPath(node.path()).ifPresent(existing -> mapped.setId(existing.getId()));
        try {
            nodeRepository.save(mapped);
        } catch (DataIntegrityViolationException duplicate) {
            Optional<ObjectNodeEntity> raced = nodeRepository.findByPath(node.path());
            if (raced.isEmpty()) {
                throw duplicate;
            }
            ObjectNodeEntity retry = mapper.toEntity(node);
            retry.setId(raced.get().getId());
            nodeRepository.save(retry);
        }
    }

    private int nextSortOrder(String parentPath) {
        return objectTree.childrenOf(parentPath).stream()
                .mapToInt(PlatformObject::sortOrder)
                .max()
                .orElse(-1) + 1;
    }

    private Object lockForVariable(String path, String name) {
        return variablePersistLocks.computeIfAbsent(path + '\0' + name, ignored -> new Object());
    }

    private void persistVariable(String path, Variable variable) {
        synchronized (lockForVariable(path, variable.name())) {
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
            entity.setUpdatedAt(mapped.getUpdatedAt());
            entity.setHistoryEnabled(mapped.isHistoryEnabled());
            entity.setHistoryRetentionDays(mapped.getHistoryRetentionDays());
            entity.setReadRolesJson(mapped.getReadRolesJson());
            entity.setWriteRolesJson(mapped.getWriteRolesJson());
            variableRepository.save(entity);
        }
    }

    @Transactional
    public void persistBindingRuleTarget(String path, Variable variable) {
        persistVariable(path, variable);
    }

    private void publish(ObjectChangeEvent event) {
        switch (event.type()) {
            case VARIABLE_UPDATED -> {
                if (event.revision() != null || event.changedBy() != null) {
                    publicationService.publishConfigVariableChangeAfterCommit(event);
                } else {
                    publicationService.publishVariableChange(
                            event.path(),
                            event.variableName(),
                            event.observedAt(),
                            event.value(),
                            event.previousValue()
                    );
                }
            }
            case EVENT_FIRED -> publicationService.publishEventFired(event.path(), event.variableName());
            case CREATED, UPDATED, DELETED -> publicationService.publishStructureChangeAfterCommit(event);
        }
    }

    /** Publishes an UPDATED event so tree clients refresh lazy children (e.g. visual group members). */
    public void publishStructureChange(String path) {
        PlatformObject node = objectTree.findByPath(path).orElse(null);
        if (node != null) {
            publish(ObjectChangeEvent.of(ObjectChangeType.UPDATED, path, node.revision(), node.lastChangedBy()));
        } else {
            publish(ObjectChangeEvent.of(ObjectChangeType.UPDATED, path));
        }
    }

    private void assertExpectedRevision(String path) {
        ObjectRevisionContext.RevisionExpectation expectation = ObjectRevisionContext.expectation();
        if (expectation == null || expectation.forceOverwrite()) {
            return;
        }
        Long expected = expectation.expectedRevision();
        if (expected == null) {
            return;
        }
        PlatformObject node = objectTree.require(path);
        if (node.revision() != expected) {
            throw new ObjectRevisionConflictException(
                    path,
                    expected,
                    node.revision(),
                    node.lastChangedBy(),
                    node.lastChangedAt()
            );
        }
    }

    private void bumpRevision(PlatformObject node) {
        node.setRevision(node.revision() + 1);
        node.setLastChangedBy(ObjectRevisionContext.actor());
        node.setLastChangedAt(Instant.now());
        persistNode(node);
    }

    private void persistNodeConfig(PlatformObject node, String changeType, String field, String summaryJson) {
        long revisionBefore = node.revision();
        bumpRevision(node);
        recordAudit(node.path(), changeType, field, revisionBefore, node.revision(), summaryJson);
    }

    private void recordAudit(
            String path,
            String changeType,
            String field,
            long revisionBefore,
            long revisionAfter,
            String summaryJson
    ) {
        configAuditService.record(
                path,
                changeType,
                field,
                ObjectRevisionContext.actor(),
                revisionBefore,
                revisionAfter,
                summaryJson
        );
    }

    private void publishConfigChange(ObjectChangeType type, String path, long revisionBefore) {
        PlatformObject node = objectTree.require(path);
        publish(ObjectChangeEvent.of(type, path, node.revision(), node.lastChangedBy()));
    }

    private void publishConfigChange(ObjectChangeEvent template, PlatformObject node) {
        publish(new ObjectChangeEvent(
                template.type(),
                template.path(),
                template.variableName(),
                Instant.now(),
                node.revision(),
                node.lastChangedBy(),
                template.telemetry(),
                template.automationEligible(),
                template.observedAt(),
                false,
                template.value(),
                template.previousValue()
        ));
    }

    /** Notifies WebSocket clients that driver runtime hints changed (status or live connection). */
    public void publishDriverRuntimeChanged(String path) {
        publish(ObjectChangeEvent.variableUpdated(path, "driverStatus"));
    }

    public List<ObjectConfigAuditService.AuditEntry> configAudit(String path, int limit) {
        return configAuditService.list(path, limit);
    }

    private static void assertUserVariable(String name) {
        if (BindingStateVariables.isReserved(name)) {
            throw new IllegalArgumentException("Cannot modify reserved variable: " + name);
        }
    }

    private static Map<String, String> metadataSnapshot(PlatformObject node) {
        Map<String, String> snapshot = new HashMap<>();
        snapshot.put("displayName", node.displayName());
        snapshot.put("description", node.description() != null ? node.description() : "");
        return snapshot;
    }

    private static Map<String, Object> variableSnapshot(Variable variable) {
        Map<String, Object> snapshot = new HashMap<>(variableDefinitionSnapshot(variable));
        snapshot.put("historyEnabled", variable.historyEnabled());
        variable.historyRetentionDays().ifPresent(days -> snapshot.put("historyRetentionDays", days));
        variable.value().ifPresent(value -> snapshot.put("value", value));
        return snapshot;
    }

    private static Map<String, Object> variableDefinitionSnapshot(Variable variable) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("readable", variable.readable());
        snapshot.put("writable", variable.writable());
        if (!variable.readRoles().isEmpty()) {
            snapshot.put("readRoles", variable.readRoles());
        }
        if (!variable.writeRoles().isEmpty()) {
            snapshot.put("writeRoles", variable.writeRoles());
        }
        return snapshot;
    }

    private static Map<String, Object> variableHistorySnapshot(Variable variable) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("historyEnabled", variable.historyEnabled());
        variable.historyRetentionDays().ifPresent(days -> snapshot.put("historyRetentionDays", days));
        snapshot.put("historySampleMode", variable.historySampleMode().name());
        snapshot.put("includePreviousValueInEvent", variable.includePreviousValueInEvent());
        snapshot.put("storageMode", variable.storageMode().name());
        variable.telemetryPublishModeOverride().ifPresent(mode -> snapshot.put("telemetryPublishMode", mode));
        return snapshot;
    }
}
