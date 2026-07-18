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

/**
 * Narrow object-tree facade for AI and other automation clients (ADR-0048).
 * Hot-path telemetry and full bootstrap APIs stay on {@link ObjectManager}.
 */
public interface ObjectTreePort {

    ObjectTree tree();

    PlatformObject require(String path);

    PlatformObject create(
            String parentPath,
            String name,
            ObjectType type,
            String displayName,
            String description,
            String templateId
    );

    void delete(String path);

    void persistNodeTree(String path);

    Variable createVariable(
            String path,
            String name,
            DataSchema schema,
            boolean readable,
            boolean writable,
            DataRecord initialValue,
            boolean historyEnabled,
            Integer historyRetentionDays
    );

    Variable setVariableValue(String path, String name, DataRecord value);

    Variable updateVariableHistory(
            String path,
            String name,
            boolean historyEnabled,
            Integer historyRetentionDays,
            String telemetryPublishMode,
            HistorySampleMode historySampleMode,
            Boolean includePreviousValueInEvent,
            VariableStorageMode storageMode
    );

    FunctionDescriptor upsertFunction(String path, FunctionDescriptor function);
}
