package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.HistorySampleMode;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.core.object.VariableStorageMode;
import org.springframework.stereotype.Component;

/**
 * Delegates {@link ObjectTreePort} to {@link ObjectManager} (ADR-0048 Wave 2).
 */
@Component
public class ObjectManagerTreePort implements ObjectTreePort {

    private final ObjectManager objectManager;

    public ObjectManagerTreePort(ObjectManager objectManager) {
        this.objectManager = objectManager;
    }

    @Override
    public ObjectTree tree() {
        return objectManager.tree();
    }

    @Override
    public PlatformObject require(String path) {
        return objectManager.require(path);
    }

    @Override
    public PlatformObject create(
            String parentPath,
            String name,
            ObjectType type,
            String displayName,
            String description,
            String templateId
    ) {
        return objectManager.create(parentPath, name, type, displayName, description, templateId);
    }

    @Override
    public void delete(String path) {
        objectManager.delete(path);
    }

    @Override
    public void persistNodeTree(String path) {
        objectManager.persistNodeTree(path);
    }

    @Override
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
        return objectManager.createVariable(
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

    @Override
    public Variable setVariableValue(String path, String name, DataRecord value) {
        return objectManager.setVariableValue(path, name, value);
    }

    @Override
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
        return objectManager.updateVariableHistory(
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

    @Override
    public FunctionDescriptor upsertFunction(String path, FunctionDescriptor function) {
        return objectManager.upsertFunction(path, function);
    }
}
