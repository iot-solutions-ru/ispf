package com.ispf.server.application.script;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintException;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.blueprint.BlueprintApplicationService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class PlatformScriptBridge {

    private static final DataSchema TEMPERATURE_SCHEMA = DataSchema.builder("temperature")
            .field("value", FieldType.DOUBLE)
            .field("unit", FieldType.STRING)
            .build();

    private final ObjectMapper objectMapper;
    private final ObjectManager objectManager;
    private final BlueprintRegistry blueprintRegistry;
    private final BlueprintApplicationService blueprintApplicationService;

    public PlatformScriptBridge(
            ObjectMapper objectMapper,
            ObjectManager objectManager,
            BlueprintRegistry blueprintRegistry,
            BlueprintApplicationService blueprintApplicationService
    ) {
        this.objectMapper = objectMapper;
        this.objectManager = objectManager;
        this.blueprintRegistry = blueprintRegistry;
        this.blueprintApplicationService = blueprintApplicationService;
    }

    public Map<String, Object> jsonParse(String source, List<String> fields) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("jsonParse source is empty");
        }
        try {
            JsonNode root = objectMapper.readTree(source.trim());
            if (!root.isObject()) {
                throw new IllegalArgumentException("jsonParse source must be a JSON object");
            }
            Map<String, Object> parsed = new LinkedHashMap<>();
            for (String field : fields) {
                JsonNode node = root.get(field);
                if (node == null || node.isNull()) {
                    parsed.put(field, null);
                } else if (node.isNumber()) {
                    parsed.put(field, node.doubleValue());
                } else {
                    parsed.put(field, node.asText());
                }
            }
            return parsed;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("jsonParse failed: " + ex.getMessage(), ex);
        }
    }

    public String readVariableField(String objectPath, String variableName, String fieldName) {
        if (objectPath == null || objectPath.isBlank()) {
            throw new IllegalArgumentException("readVariable objectPath is required");
        }
        if (variableName == null || variableName.isBlank()) {
            throw new IllegalArgumentException("readVariable variable is required");
        }
        PlatformObject node = objectManager.require(objectPath);
        Variable variable = node.getVariable(variableName)
                .orElseThrow(() -> new IllegalArgumentException("Variable not found: " + variableName));
        DataRecord value = variable.value().orElseThrow(
                () -> new IllegalArgumentException("Variable has no value: " + variableName)
        );
        if (value.rowCount() == 0) {
            return "";
        }
        String field = fieldName == null || fieldName.isBlank() ? "value" : fieldName;
        Object raw = value.firstRow().get(field);
        return raw == null ? "" : String.valueOf(raw);
    }

    public String instantiateModelIfMissing(String blueprintName, String parentPath, String instanceName) {
        validateInstanceName(instanceName);
        if (blueprintName == null || blueprintName.isBlank()) {
            throw new IllegalArgumentException("instantiateModelIfMissing blueprintName is required");
        }
        if (parentPath == null || parentPath.isBlank()) {
            throw new IllegalArgumentException("instantiateModelIfMissing parentPath is required");
        }
        String fullPath = objectManager.tree().resolveChildPath(parentPath, instanceName);
        if (objectManager.tree().findByPath(fullPath).isPresent()) {
            return fullPath;
        }
        BlueprintDefinition model = blueprintRegistry.findByName(blueprintName)
                .orElseThrow(() -> new IllegalArgumentException("Blueprint not found: " + blueprintName));
        try {
            blueprintApplicationService.instantiateWithRules(model.id(), parentPath, instanceName, Map.of());
            objectManager.persistNodeTree(fullPath);
            return fullPath;
        } catch (IllegalArgumentException ex) {
            if (ex.getCause() instanceof BlueprintException modelEx
                    && modelEx.getMessage() != null
                    && modelEx.getMessage().contains("Object already exists")) {
                return fullPath;
            }
            throw ex;
        }
    }

    public void setDriverTelemetry(String objectPath, String variableName, Map<String, Object> fields) {
        if (objectPath == null || objectPath.isBlank()) {
            throw new IllegalArgumentException("setDriverTelemetry objectPath is required");
        }
        objectManager.require(objectPath);
        String name = variableName == null || variableName.isBlank() ? "temperature" : variableName;
        Object valueRaw = fields.get("value");
        double value = parseFiniteDouble(valueRaw);
        String unit = Optional.ofNullable(fields.get("unit")).map(String::valueOf).filter(s -> !s.isBlank()).orElse("C");
        DataRecord record = DataRecord.single(TEMPERATURE_SCHEMA, Map.of("value", value, "unit", unit));
        objectManager.setDriverTelemetryValue(objectPath, name, record);
    }

    public void writeVariableFields(String objectPath, String variableName, Map<String, Object> fields) {
        if (objectPath == null || objectPath.isBlank()) {
            throw new IllegalArgumentException("writeVariable objectPath is required");
        }
        if (variableName == null || variableName.isBlank()) {
            throw new IllegalArgumentException("writeVariable variable is required");
        }
        PlatformObject node = objectManager.require(objectPath);
        com.ispf.core.object.Variable variable = node.getVariable(variableName)
                .orElseThrow(() -> new IllegalArgumentException("Variable not found: " + variableName));
        if (!variable.writable()) {
            throw new IllegalArgumentException("Variable is not writable: " + variableName);
        }
        Map<String, Object> coerced = new LinkedHashMap<>();
        for (com.ispf.core.model.FieldDefinition field : variable.schema().fields()) {
            coerced.put(field.name(), ScriptFieldCoercion.coerce(field, fields.get(field.name())));
        }
        objectManager.setRuntimeVariableValue(
                objectPath,
                variableName,
                DataRecord.single(variable.schema(), coerced),
                false
        );
    }

    static void validateInstanceName(String instanceName) {
        if (instanceName == null || instanceName.isBlank()) {
            throw new IllegalArgumentException("instance name is required");
        }
        if (instanceName.contains(".")) {
            throw new IllegalArgumentException("instance name must not contain dots: " + instanceName);
        }
    }

    static double parseFiniteDouble(Object raw) {
        if (raw == null) {
            throw new IllegalArgumentException("temperature value is required");
        }
        try {
            double value = raw instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(raw).trim());
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("temperature is not finite: " + raw);
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("temperature is not numeric: " + raw, ex);
        }
    }

    static List<String> readStringFields(JsonNode fieldsNode) {
        List<String> fields = new ArrayList<>();
        if (fieldsNode == null || !fieldsNode.isArray()) {
            return fields;
        }
        for (JsonNode item : fieldsNode) {
            String text = item.asText("").trim();
            if (!text.isBlank()) {
                fields.add(text);
            }
        }
        return fields;
    }
}
