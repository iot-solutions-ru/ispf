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
import com.ispf.server.bootstrap.PlatformBootstrap;
import com.ispf.server.bootstrap.SystemObjectDescriptions;
import com.ispf.server.config.BootstrapProperties;
import com.ispf.server.federation.FederationPaths;
import com.ispf.server.persistence.ObjectEntityMapper;
import com.ispf.server.persistence.ObjectNodeRepository;
import com.ispf.server.persistence.ObjectVariableRepository;
import com.ispf.server.persistence.entity.ObjectNodeEntity;
import com.ispf.server.persistence.entity.ObjectVariableEntity;
import com.ispf.server.object.pubsub.ObjectChangePublicationService;
import com.ispf.server.platform.ClusterPlatformBootstrapService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central facade for object tree operations with database persistence and change events.
 * Tree CRUD, variable config ops, and blueprint bootstrap live in focused collaborators
 * (ADR-0048 Wave 4); cluster replica sync continues to call reload/sync APIs here.
 */
@Service
public class ObjectManager {

    private final ObjectTree objectTree = new ObjectTree();
    private final ObjectNodeRepository nodeRepository;
    private final ObjectVariableRepository variableRepository;
    private final ObjectEntityMapper mapper;
    private final PlatformBootstrap platformBootstrap;
    private final BootstrapProperties bootstrapProperties;
    private final ObjectChangePublicationService publicationService;
    private final ObjectProvider<ObjectManager> self;
    private final ObjectConfigAuditService configAuditService;
    private final ObjectProvider<ClusterPlatformBootstrapService> clusterBootstrapService;
    private final ObjectProvider<DriverTelemetryService> driverTelemetryService;
    private final ObjectTreeBootstrapFacade bootstrapFacade;
    private final TreeCrudService treeCrudService;
    private final ObjectVariableService variableService;
    private final ObjectTreeLoadSyncService loadSyncService;
    private final ObjectMetadataService metadataService;
    private final ConcurrentHashMap<String, Object> variablePersistLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> nodePersistLocks = new ConcurrentHashMap<>();
    private volatile boolean initialized;

    public ObjectManager(
            ObjectNodeRepository nodeRepository,
            ObjectVariableRepository variableRepository,
            ObjectEntityMapper mapper,
            PlatformBootstrap platformBootstrap,
            BootstrapProperties bootstrapProperties,
            @org.springframework.context.annotation.Lazy ObjectChangePublicationService publicationService,
            ObjectProvider<ObjectManager> self,
            ObjectConfigAuditService configAuditService,
            ObjectProvider<ClusterPlatformBootstrapService> clusterBootstrapService,
            ObjectProvider<DriverTelemetryService> driverTelemetryService,
            ObjectTreeBootstrapFacade bootstrapFacade,
            @Lazy TreeCrudService treeCrudService,
            @Lazy ObjectVariableService variableService,
            @Lazy ObjectTreeLoadSyncService loadSyncService,
            @Lazy ObjectMetadataService metadataService
    ) {
        this.nodeRepository = nodeRepository;
        this.variableRepository = variableRepository;
        this.mapper = mapper;
        this.platformBootstrap = platformBootstrap;
        this.bootstrapProperties = bootstrapProperties;
        this.publicationService = publicationService;
        this.self = self;
        this.configAuditService = configAuditService;
        this.clusterBootstrapService = clusterBootstrapService;
        this.driverTelemetryService = driverTelemetryService;
        this.bootstrapFacade = bootstrapFacade;
        this.treeCrudService = treeCrudService;
        this.variableService = variableService;
        this.loadSyncService = loadSyncService;
        this.metadataService = metadataService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Transactional
    public synchronized void initialize() {
        if (initialized) {
            return;
        }
        bootstrapFacade.prepareClusterFixtureRole();
        if (nodeRepository.existsByPath("root.platform")) {
            loadSyncService.loadFromDatabase();
            ensureBootstrapNodes();
        } else {
            seedPlatformStructure();
        }
        bootstrapFacade.runCatalogAndFixtures(this);
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
        return treeCrudService.create(parentPath, name, type, displayName, description, templateId);
    }

    @Transactional
    public void reorderChildren(String parentPath, List<String> orderedPaths) {
        treeCrudService.reorderChildren(parentPath, orderedPaths);
    }

    /** Direct children visible in the structural tree (excludes visual-group members). */
    List<PlatformObject> structuralChildrenOf(String parentPath) {
        return treeCrudService.structuralChildrenOf(parentPath);
    }

    @Transactional
    public PlatformObject reconcileType(String path, ObjectType expectedType) {
        return treeCrudService.reconcileType(path, expectedType);
    }

    @Transactional
    public void ensureSystemCatalogFolder(String path, ObjectType type, String templateId) {
        treeCrudService.ensureSystemCatalogFolder(path, type, templateId);
    }

    @Transactional
    public PlatformObject updateInfo(String path, String displayName, String description) {
        return metadataService.updateInfo(path, displayName, description);
    }

    @Transactional
    public PlatformObject updateBindingAuditEnabled(String path, boolean enabled) {
        return metadataService.updateBindingAuditEnabled(path, enabled);
    }

    public boolean isBindingAuditEnabled(String path) {
        return metadataService.isBindingAuditEnabled(path);
    }

    @Transactional
    public PlatformObject updateFunctionAuditEnabled(String path, boolean enabled) {
        return metadataService.updateFunctionAuditEnabled(path, enabled);
    }

    public boolean isFunctionAuditEnabled(String path) {
        return metadataService.isFunctionAuditEnabled(path);
    }

    @Transactional
    public PlatformObject updateEventJournalEnabled(String path, boolean enabled) {
        return metadataService.updateEventJournalEnabled(path, enabled);
    }

    public boolean isEventJournalEnabled(String path) {
        return metadataService.isEventJournalEnabled(path);
    }

    @Transactional
    public void delete(String path) {
        treeCrudService.delete(path);
    }

    public Variable setDriverTelemetryValue(String path, String name, DataRecord value) {
        return driverTelemetryService.getObject().setDriverTelemetryValue(path, name, value);
    }

    public Variable setDriverTelemetryValue(String path, String name, DataRecord value, Instant observedAt) {
        return driverTelemetryService.getObject().setDriverTelemetryValue(path, name, value, observedAt);
    }

    /**
     * Event-journal-only fast path: skip RAM live-value update — journal stores do not need
     * object-tree computed value per tick. Historian-only path updates RAM (see above).
     */
    Variable resolveDriverTelemetryVariable(String path, String name, DataRecord value) {
        PlatformObject node = objectTree.require(path);
        return node.getVariable(name).orElseGet(() -> {
            Variable created = new Variable(name, value.schema(), true, false, null);
            node.addVariable(created);
            return created;
        });
    }

    public Variable setDriverTelemetryValueDirect(String path, String name, DataRecord value) {
        return applyDriverTelemetryInMemory(path, name, value);
    }

    Variable applyDriverTelemetryInMemory(String path, String name, DataRecord value) {
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
            publish(ObjectChangeEvent.variableUpdated(path, name, false, true, null, value, null));
        }
        return variable;
    }

    @Transactional
    public Variable setVariableValue(String path, String name, DataRecord value) {
        return variableService.setVariableValue(path, name, value);
    }

    @Transactional
    public Variable setVariableValue(String path, String name, DataRecord value, Instant observedAt) {
        return variableService.setVariableValue(path, name, value, observedAt);
    }

    @Transactional
    public void deleteVariable(String path, String name) {
        variableService.deleteVariable(path, name);
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
        return variableService.createVariable(
                path,
                name,
                schema,
                readable,
                writable,
                initialValue,
                historyEnabled,
                historyRetentionDays
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
        return variableService.createVariable(
                path,
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
    }

    @Transactional
    public Variable updateVariableDefinition(
            String path,
            String name,
            Boolean readable,
            Boolean writable
    ) {
        return variableService.updateVariableDefinition(path, name, readable, writable);
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
        return variableService.updateVariableDefinition(path, name, readable, writable, readRoles, writeRoles);
    }

    @Transactional
    public FunctionDescriptor upsertFunction(String path, FunctionDescriptor function) {
        return variableService.upsertFunction(path, function);
    }

    @Transactional
    public void deleteFunction(String path, String name) {
        variableService.deleteFunction(path, name);
    }

    @Transactional
    public EventDescriptor upsertEvent(String path, EventDescriptor event) {
        return variableService.upsertEvent(path, event);
    }

    @Transactional
    public void deleteEvent(String path, String name) {
        variableService.deleteEvent(path, name);
    }

    @Transactional
    public Variable updateVariableHistory(
            String path,
            String name,
            boolean historyEnabled,
            Integer historyRetentionDays,
            String telemetryPublishMode
    ) {
        return variableService.updateVariableHistory(
                path,
                name,
                historyEnabled,
                historyRetentionDays,
                telemetryPublishMode
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
        return variableService.updateVariableHistory(
                path,
                name,
                historyEnabled,
                historyRetentionDays,
                telemetryPublishMode,
                historySampleMode,
                includePreviousValueInEvent,
                storageMode
        );
    }

    @Transactional
    public Variable setSystemVariableValue(String path, String name, DataRecord value) {
        return variableService.setSystemVariableValue(path, name, value);
    }

    @Transactional
    public Variable upsertSystemVariable(
            String path,
            String name,
            DataSchema schema,
            DataRecord value
    ) {
        return variableService.upsertSystemVariable(path, name, schema, value);
    }

    @Transactional
    public void persistNodeTree(String path) {
        treeCrudService.persistNodeTree(path);
    }

    private void ensureBootstrapNodes() {
        ensureBootstrapNode("root.platform.operator-apps", ObjectType.OPERATOR_APPS, "app-folder-v1");
        ensureBootstrapNode("root.platform.alert-rules", ObjectType.ALERT_RULES, null);
        ensureBootstrapNode("root.platform.correlators", ObjectType.CORRELATORS, null);
        ensureBootstrapNode("root.platform.queries", ObjectType.QUERIES, null);
        ensureBootstrapNode("root.platform.event-filters", ObjectType.EVENT_FILTERS, null);
        ensureBootstrapNode("root.platform.event-frames", ObjectType.EVENT_FRAMES, null);
        if (bootstrapProperties.isMesCatalogEnabled()) {
            ensureBootstrapNode("root.platform.mes", ObjectType.MES, null);
        }
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
            loadSyncService.registerNodeFromEntity(
                    persisted.get(),
                    loadSyncService.loadVariablesByPath(List.of(path)).getOrDefault(path, List.of())
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
        loadSyncService.reloadFromDatabase(this::ensureBootstrapNodes);
    }

    public synchronized void syncPathFromDatabase(String path) {
        loadSyncService.syncPathFromDatabase(path);
    }

    /**
     * Reloads node metadata and all persisted variables from PostgreSQL (cluster follower sync).
     * Removes RAM variables absent in PG; drops the path from RAM when the node no longer exists.
     */
    public synchronized void reloadPathFromDatabase(String path) {
        loadSyncService.reloadPathFromDatabase(path);
    }

    /** Reloads a persisted config variable from PostgreSQL (cluster follower sync). */
    public synchronized void syncVariableFromDatabase(String path, String name) {
        loadSyncService.syncVariableFromDatabase(path, name);
    }

    public synchronized void removePathFromMemoryIfPresent(String path) {
        if (path == null || path.isBlank() || "root".equals(path)) {
            return;
        }
        if (objectTree.findByPath(path).isPresent()) {
            objectTree.delete(path);
        }
    }

    void persistNode(PlatformObject node) {
        if (node.id() == null || node.id().isBlank()) {
            throw new IllegalStateException("Cannot persist node without id: " + node.path());
        }
        synchronized (lockForNode(node.path())) {
            Optional<ObjectNodeEntity> existing = nodeRepository.findByPath(node.path());
            if (existing.isPresent()) {
                ObjectNodeEntity entity = existing.get();
                mapper.copyNodeFields(entity, node);
                nodeRepository.save(entity);
                return;
            }
            // Inserts run in REQUIRES_NEW so a duplicate-key race rolls back only the short TX
            // and does not mark the outer ApplicationReady transaction rollback-only / poison
            // the Hibernate session (AssertionFailure on later flush).
            try {
                self.getObject().insertNodeInNewTx(node);
            } catch (DataIntegrityViolationException duplicate) {
                ObjectNodeEntity raced = nodeRepository.findByPath(node.path()).orElseThrow(() -> duplicate);
                mapper.copyNodeFields(raced, node);
                nodeRepository.save(raced);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertNodeInNewTx(PlatformObject node) {
        if (nodeRepository.findByPath(node.path()).isPresent()) {
            return;
        }
        ObjectNodeEntity created = new ObjectNodeEntity();
        created.setId(node.id());
        mapper.copyNodeFields(created, node);
        nodeRepository.saveAndFlush(created);
    }

    Object lockForVariable(String path, String name) {
        return variablePersistLocks.computeIfAbsent(path + '\0' + name, ignored -> new Object());
    }

    private Object lockForNode(String path) {
        return nodePersistLocks.computeIfAbsent(path, ignored -> new Object());
    }

    void persistVariable(String path, Variable variable) {
        synchronized (lockForVariable(path, variable.name())) {
            Optional<ObjectVariableEntity> existing =
                    variableRepository.findByObjectPathAndName(path, variable.name());
            if (existing.isPresent()) {
                ObjectVariableEntity entity = existing.get();
                mapper.copyVariableFields(entity, path, variable);
                variableRepository.save(entity);
                return;
            }
            try {
                self.getObject().insertVariableInNewTx(path, variable);
            } catch (DataIntegrityViolationException duplicate) {
                ObjectVariableEntity raced = variableRepository
                        .findByObjectPathAndName(path, variable.name())
                        .orElseThrow(() -> duplicate);
                mapper.copyVariableFields(raced, path, variable);
                variableRepository.save(raced);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertVariableInNewTx(String path, Variable variable) {
        if (variableRepository.findByObjectPathAndName(path, variable.name()).isPresent()) {
            return;
        }
        ObjectVariableEntity created = new ObjectVariableEntity();
        mapper.copyVariableFields(created, path, variable);
        variableRepository.saveAndFlush(created);
    }

    @Transactional
    public void persistBindingRuleTarget(String path, Variable variable) {
        // TRANSIENT targets stay RAM-only after setComputedValue — skip DB churn on hot ticks.
        if (variable != null && variable.storageMode() == VariableStorageMode.TRANSIENT) {
            return;
        }
        persistVariable(path, variable);
    }

    void publish(ObjectChangeEvent event) {
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

    void assertExpectedRevision(String path) {
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

    void bumpRevision(PlatformObject node) {
        node.setRevision(node.revision() + 1);
        node.setLastChangedBy(ObjectRevisionContext.actor());
        node.setLastChangedAt(Instant.now());
        persistNode(node);
    }

    void persistNodeConfig(PlatformObject node, String changeType, String field, String summaryJson) {
        long revisionBefore = node.revision();
        bumpRevision(node);
        recordAudit(node.path(), changeType, field, revisionBefore, node.revision(), summaryJson);
    }

    void recordAudit(
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

    void publishConfigChange(ObjectChangeType type, String path, long revisionBefore) {
        PlatformObject node = objectTree.require(path);
        publish(ObjectChangeEvent.of(type, path, node.revision(), node.lastChangedBy()));
    }

    void publishConfigChange(ObjectChangeEvent template, PlatformObject node) {
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
}
