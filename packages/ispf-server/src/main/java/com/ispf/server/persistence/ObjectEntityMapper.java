package com.ispf.server.persistence;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.HistorySampleMode;
import com.ispf.core.object.VariableStorageMode;
import com.ispf.core.object.Variable;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.server.persistence.entity.ObjectNodeEntity;
import com.ispf.server.persistence.entity.ObjectVariableEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ObjectEntityMapper {

    private final ObjectMapper objectMapper;

    public ObjectEntityMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNodeEntity toEntity(PlatformObject node) {
        ObjectNodeEntity entity = new ObjectNodeEntity();
        entity.setId(node.id());
        entity.setPath(node.path());
        entity.setType(node.type());
        entity.setDisplayName(node.displayName());
        entity.setDescription(node.description());
        entity.setTemplateId(node.templateId().orElse(null));
        entity.setappliedBlueprintIdsJson(writeappliedBlueprintIds(node.appliedBlueprintIds()));
        entity.setCreatedAt(node.createdAt());
        entity.setSortOrder(node.sortOrder());
        entity.setEventsJson(writeJson(node.events().values().toArray(new EventDescriptor[0])));
        entity.setFunctionsJson(writeJson(node.functions().values().toArray(new FunctionDescriptor[0])));
        entity.setRevision(node.revision());
        entity.setLastChangedBy(node.lastChangedBy());
        entity.setLastChangedAt(node.lastChangedAt());
        entity.setBindingAuditEnabled(node.bindingAuditEnabled());
        entity.setFunctionAuditEnabled(node.functionAuditEnabled());
        entity.setEventJournalEnabled(node.eventJournalEnabled());
        return entity;
    }

    public ObjectVariableEntity toEntity(String objectPath, Variable variable) {
        ObjectVariableEntity entity = new ObjectVariableEntity();
        entity.setObjectPath(objectPath);
        entity.setName(variable.name());
        entity.setSchemaJson(writeJson(variable.schema()));
        entity.setValueJson(variable.value().map(this::writeJson).orElse(null));
        entity.setReadable(variable.readable());
        entity.setWritable(variable.writable());
        entity.setUpdatedAt(variable.updatedAt().orElse(null));
        entity.setHistoryEnabled(variable.historyEnabled());
        entity.setHistoryRetentionDays(variable.historyRetentionDays().orElse(null));
        entity.setHistorySampleMode(variable.historySampleMode().name());
        entity.setIncludePreviousValueInEvent(variable.includePreviousValueInEvent());
        entity.setStorageMode(variable.storageMode().name());
        entity.setTelemetryPublishMode(variable.telemetryPublishModeOverride().orElse(null));
        if (variable.storageMode() == VariableStorageMode.TRANSIENT) {
            entity.setValueJson(null);
        }
        entity.setReadRolesJson(writeStringList(variable.readRoles()));
        entity.setWriteRolesJson(writeStringList(variable.writeRoles()));
        return entity;
    }

    public Variable toVariable(ObjectVariableEntity entity) {
        return new Variable(
                entity.getName(),
                readSchema(entity.getSchemaJson()),
                entity.isReadable(),
                entity.isWritable(),
                readDataRecord(entity.getValueJson()),
                entity.isHistoryEnabled(),
                entity.getHistoryRetentionDays(),
                HistorySampleMode.parse(entity.getHistorySampleMode()),
                entity.isIncludePreviousValueInEvent(),
                VariableStorageMode.parse(entity.getStorageMode()),
                entity.getTelemetryPublishMode(),
                readStringList(entity.getReadRolesJson()),
                readStringList(entity.getWriteRolesJson())
        );
    }

    public List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            String[] values = objectMapper.readValue(json, String[].class);
            return List.of(values);
        } catch (JacksonException e) {
            return List.of();
        }
    }

    private String writeStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return writeJson(values.toArray(new String[0]));
    }

    public DataSchema readSchema(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Schema JSON is empty");
        }
        try {
            return objectMapper.readValue(json, DataSchema.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("Invalid schema JSON", e);
        }
    }

    public EventDescriptor[] readEvents(String json) {
        if (json == null || json.isBlank()) {
            return new EventDescriptor[0];
        }
        try {
            return objectMapper.readValue(json, EventDescriptor[].class);
        } catch (JacksonException e) {
            return new EventDescriptor[0];
        }
    }

    public FunctionDescriptor[] readFunctions(String json) {
        if (json == null || json.isBlank()) {
            return new FunctionDescriptor[0];
        }
        try {
            return objectMapper.readValue(json, FunctionDescriptor[].class);
        } catch (JacksonException e) {
            return new FunctionDescriptor[0];
        }
    }

    public DataRecord readDataRecord(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, DataRecord.class);
        } catch (JacksonException e) {
            return null;
        }
    }

    public String writeDataRecord(DataRecord record) {
        return writeJson(record);
    }

    /** JSON object {@code {"before":…,"after":…}} for object_config_audit.summary_json. */
    public String auditDiff(Object before, Object after) {
        java.util.Map<String, Object> summary = new java.util.LinkedHashMap<>();
        if (before != null) {
            summary.put("before", before);
        }
        if (after != null) {
            summary.put("after", after);
        }
        if (summary.isEmpty()) {
            return null;
        }
        return writeJson(summary);
    }

    public List<String> readappliedBlueprintIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            String[] ids = objectMapper.readValue(json, String[].class);
            return List.of(ids);
        } catch (JacksonException e) {
            return List.of();
        }
    }

    public String writeappliedBlueprintIds(List<String> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) {
            return null;
        }
        return writeJson(modelIds.toArray(new String[0]));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("JSON serialization failed", e);
        }
    }
}
