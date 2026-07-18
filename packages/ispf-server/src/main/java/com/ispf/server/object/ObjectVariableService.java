package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.HistorySampleMode;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.core.object.VariableStorageMode;
import com.ispf.server.driver.DeviceTelemetryPolicyService;
import com.ispf.server.driver.TelemetryPublishMode;
import com.ispf.server.function.java.JavaFunctionRuntimeService;
import com.ispf.server.persistence.ObjectEntityMapper;
import com.ispf.server.persistence.ObjectVariableRepository;
import com.ispf.server.persistence.entity.ObjectVariableEntity;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Variable / function / event config ops used widely across the platform.
 * Extracted from {@link ObjectManager} (ADR-0048 Wave 4). Hot-path driver telemetry stays elsewhere.
 */
@Service
public class ObjectVariableService {

    private final ObjectManager objectManager;
    private final ObjectVariableRepository variableRepository;
    private final ObjectEntityMapper mapper;
    private final DeviceTelemetryPolicyService telemetryPolicyService;
    private final JavaFunctionRuntimeService javaFunctionRuntimeService;

    public ObjectVariableService(
            @Lazy ObjectManager objectManager,
            ObjectVariableRepository variableRepository,
            ObjectEntityMapper mapper,
            @Lazy DeviceTelemetryPolicyService telemetryPolicyService,
            JavaFunctionRuntimeService javaFunctionRuntimeService
    ) {
        this.objectManager = objectManager;
        this.variableRepository = variableRepository;
        this.mapper = mapper;
        this.telemetryPolicyService = telemetryPolicyService;
        this.javaFunctionRuntimeService = javaFunctionRuntimeService;
    }

    @Transactional
    public Variable setVariableValue(String path, String name, DataRecord value) {
        return setVariableValue(path, name, value, null);
    }

    @Transactional
    public Variable setVariableValue(String path, String name, DataRecord value, Instant observedAt) {
        assertUserVariable(name);
        objectManager.assertExpectedRevision(path);
        PlatformObject node = objectManager.tree().require(path);
        long revisionBefore = node.revision();
        DataRecord before = node.getVariable(name).flatMap(Variable::value).orElse(null);
        node.setVariableValue(name, value);
        Variable variable = node.getVariable(name).orElseThrow();
        objectManager.persistVariable(path, variable);
        objectManager.bumpRevision(node);
        objectManager.recordAudit(
                path,
                "SET_VARIABLE_VALUE",
                name,
                revisionBefore,
                node.revision(),
                mapper.auditDiff(before, value)
        );
        objectManager.publishConfigChange(
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
        objectManager.assertExpectedRevision(path);
        PlatformObject node = objectManager.tree().require(path);
        Optional<Variable> existing = node.getVariable(name);
        if (existing.isEmpty()) {
            return;
        }
        long revisionBefore = node.revision();
        Map<String, Object> before = variableSnapshot(existing.get());
        node.removeVariable(name);
        variableRepository.deleteByObjectPathAndName(path, name);
        objectManager.bumpRevision(node);
        objectManager.recordAudit(
                path,
                "DELETE_VARIABLE",
                name,
                revisionBefore,
                node.revision(),
                mapper.auditDiff(before, null)
        );
        objectManager.publishConfigChange(ObjectChangeEvent.variableUpdated(path, name), node);
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
        PlatformObject node = objectManager.tree().require(path);
        if (node.getVariable(name).isPresent()) {
            throw new IllegalArgumentException("Variable already exists: " + name);
        }
        objectManager.assertExpectedRevision(path);
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
        objectManager.persistVariable(path, variable);
        objectManager.bumpRevision(node);
        objectManager.recordAudit(
                path,
                "CREATE_VARIABLE",
                name,
                revisionBefore,
                node.revision(),
                mapper.auditDiff(null, variableSnapshot(variable))
        );
        objectManager.publishConfigChange(ObjectChangeEvent.variableUpdated(path, name), node);
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
        objectManager.assertExpectedRevision(path);
        PlatformObject node = objectManager.tree().require(path);
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
        objectManager.persistVariable(path, updated);
        objectManager.bumpRevision(node);
        objectManager.recordAudit(
                path,
                "UPDATE_VARIABLE",
                name,
                revisionBefore,
                node.revision(),
                mapper.auditDiff(before, variableDefinitionSnapshot(updated))
        );
        objectManager.publishConfigChange(ObjectChangeEvent.variableUpdated(path, name), node);
        return updated;
    }

    @Transactional
    public FunctionDescriptor upsertFunction(String path, FunctionDescriptor function) {
        if (function == null || function.name() == null || function.name().isBlank()) {
            throw new IllegalArgumentException("Function name is required");
        }
        objectManager.assertExpectedRevision(path);
        PlatformObject node = objectManager.tree().require(path);
        long revisionBefore = node.revision();
        FunctionDescriptor before = node.functions().get(function.name());
        javaFunctionRuntimeService.syncOnSave(path, function, before);
        node.addFunction(function);
        objectManager.persistNodeConfig(
                node,
                "UPSERT_FUNCTION",
                function.name(),
                mapper.auditDiff(before, function)
        );
        objectManager.publishConfigChange(ObjectChangeType.UPDATED, path, revisionBefore);
        return function;
    }

    @Transactional
    public void deleteFunction(String path, String name) {
        objectManager.assertExpectedRevision(path);
        PlatformObject node = objectManager.tree().require(path);
        FunctionDescriptor before = node.functions().get(name);
        if (before == null) {
            return;
        }
        long revisionBefore = node.revision();
        node.removeFunction(name);
        javaFunctionRuntimeService.unregister(path, name);
        objectManager.persistNodeConfig(node, "DELETE_FUNCTION", name, mapper.auditDiff(before, null));
        objectManager.publishConfigChange(ObjectChangeType.UPDATED, path, revisionBefore);
    }

    @Transactional
    public EventDescriptor upsertEvent(String path, EventDescriptor event) {
        if (event == null || event.name() == null || event.name().isBlank()) {
            throw new IllegalArgumentException("Event name is required");
        }
        objectManager.assertExpectedRevision(path);
        PlatformObject node = objectManager.tree().require(path);
        long revisionBefore = node.revision();
        EventDescriptor before = node.events().get(event.name());
        node.addEvent(event);
        objectManager.persistNodeConfig(node, "UPSERT_EVENT", event.name(), mapper.auditDiff(before, event));
        objectManager.publishConfigChange(ObjectChangeType.UPDATED, path, revisionBefore);
        return event;
    }

    @Transactional
    public void deleteEvent(String path, String name) {
        objectManager.assertExpectedRevision(path);
        PlatformObject node = objectManager.tree().require(path);
        EventDescriptor before = node.events().get(name);
        if (before == null) {
            return;
        }
        long revisionBefore = node.revision();
        node.removeEvent(name);
        objectManager.persistNodeConfig(node, "DELETE_EVENT", name, mapper.auditDiff(before, null));
        objectManager.publishConfigChange(ObjectChangeType.UPDATED, path, revisionBefore);
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
        objectManager.assertExpectedRevision(path);
        PlatformObject node = objectManager.tree().require(path);
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
        objectManager.persistVariable(path, updated);
        objectManager.bumpRevision(node);
        telemetryPolicyService.invalidateVariable(path, name);
        objectManager.recordAudit(
                path,
                "UPDATE_VARIABLE_HISTORY",
                name,
                revisionBefore,
                node.revision(),
                mapper.auditDiff(before, variableHistorySnapshot(updated))
        );
        objectManager.publishConfigChange(ObjectChangeEvent.variableUpdated(path, name), node);
        return updated;
    }

    @Transactional
    public Variable setSystemVariableValue(String path, String name, DataRecord value) {
        PlatformObject node = objectManager.tree().require(path);
        Variable variable = node.getVariable(name)
                .orElseThrow(() -> new IllegalArgumentException("Unknown variable: " + name));
        if (variable.writable()) {
            variable.setValue(value);
        } else {
            variable.setComputedValue(value);
        }
        objectManager.persistVariable(path, variable);
        objectManager.publish(ObjectChangeEvent.variableUpdated(path, name));
        return variable;
    }

    @Transactional
    public Variable upsertSystemVariable(
            String path,
            String name,
            DataSchema schema,
            DataRecord value
    ) {
        synchronized (objectManager.lockForVariable(path, name)) {
            PlatformObject node = objectManager.tree().require(path);
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
            // Do not catch DataIntegrityViolationException here: after a failed IDENTITY insert
            // Hibernate marks the session unusable; continuing poisons ApplicationReady bootstrap.
            objectManager.persistVariable(path, created);
            objectManager.publish(ObjectChangeEvent.variableUpdated(path, name));
            return created;
        }
    }

    private static void assertUserVariable(String name) {
        if (BindingStateVariables.isReserved(name)) {
            throw new IllegalArgumentException("Cannot modify reserved variable: " + name);
        }
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
