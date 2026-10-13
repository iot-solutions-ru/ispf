package com.ispf.server.application.script;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldDefinition;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.core.object.Variable;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintException;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.server.bootstrap.PlatformReferenceBlueprintBootstrap;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.blueprint.BlueprintApplicationService;
import com.ispf.server.query.ObjectQueryPatchService;
import com.ispf.server.query.ObjectQueryService;
import com.ispf.server.query.oq.ObjectQueryResult;
import com.ispf.server.query.oq.ObjectQuerySpec;
import com.ispf.server.query.oq.ObjectQuerySpecParser;
import com.ispf.server.security.acl.VariableAclRequestContext;
import com.ispf.server.security.acl.VariableMemberAccessService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PlatformScriptBridge {

    private final ObjectMapper objectMapper;
    private final ObjectManager objectManager;
    private final BlueprintRegistry blueprintRegistry;
    private final BlueprintApplicationService blueprintApplicationService;
    private final ObjectProvider<PlatformReferenceBlueprintBootstrap> platformReferenceBlueprintBootstrap;
    private final ObjectQuerySpecParser objectQuerySpecParser;
    private final ObjectQueryService objectQueryService;
    private final ObjectQueryPatchService objectQueryPatchService;
    private final VariableMemberAccessService variableMemberAccessService;

    public PlatformScriptBridge(
            ObjectMapper objectMapper,
            ObjectManager objectManager,
            BlueprintRegistry blueprintRegistry,
            BlueprintApplicationService blueprintApplicationService,
            ObjectProvider<PlatformReferenceBlueprintBootstrap> platformReferenceBlueprintBootstrap,
            @Lazy ObjectQueryService objectQueryService,
            @Lazy ObjectQueryPatchService objectQueryPatchService,
            VariableMemberAccessService variableMemberAccessService
    ) {
        this.objectMapper = objectMapper;
        this.objectManager = objectManager;
        this.blueprintRegistry = blueprintRegistry;
        this.blueprintApplicationService = blueprintApplicationService;
        this.platformReferenceBlueprintBootstrap = platformReferenceBlueprintBootstrap;
        this.objectQuerySpecParser = new ObjectQuerySpecParser(objectMapper);
        this.objectQueryService = objectQueryService;
        this.objectQueryPatchService = objectQueryPatchService;
        this.variableMemberAccessService = variableMemberAccessService;
    }

    public List<Map<String, Object>> queryRows(String specJson, String callerObjectPath) {
        if (specJson == null || specJson.isBlank()) {
            throw new IllegalArgumentException("queryRows spec is required");
        }
        ObjectQuerySpec spec = objectQuerySpecParser.parse(specJson);
        ObjectQueryResult result = objectQueryService.execute(spec, callerObjectPath);
        return result.rows();
    }

    public int applyQueryPatch(List<Map<String, Object>> patches, String callerObjectPath) {
        if (patches == null || patches.isEmpty()) {
            return 0;
        }
        try {
            String json = objectMapper.writeValueAsString(patches);
            return objectQueryPatchService.apply(json, callerObjectPath).applied();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to apply query patch: " + ex.getMessage(), ex);
        }
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
        if (VariableAclRequestContext.isMemberEnforced()
                && !variableMemberAccessService.canRead(
                        objectPath,
                        variableName,
                        VariableAclRequestContext.requireAuthentication()
                )) {
            return null;
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
        objectManager.syncPathFromDatabase(fullPath);
        if (objectManager.tree().findByPath(fullPath).isPresent()) {
            return fullPath;
        }
        platformReferenceBlueprintBootstrap.ifAvailable(PlatformReferenceBlueprintBootstrap::ensureReferenceModels);
        BlueprintDefinition model = blueprintRegistry.findByName(blueprintName)
                .orElseThrow(() -> new IllegalArgumentException("Blueprint not found: " + blueprintName));
        try {
            blueprintApplicationService.instantiateWithRules(model.id(), parentPath, instanceName, Map.of());
            objectManager.syncPathFromDatabase(fullPath);
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
        if (variableName == null || variableName.isBlank()) {
            throw new IllegalArgumentException("setDriverTelemetry variable is required");
        }
        PlatformObject node = objectManager.require(objectPath);
        Variable variable = node.getVariable(variableName)
                .orElseThrow(() -> new IllegalArgumentException("Variable not found: " + variableName));
        objectManager.setDriverTelemetryValue(objectPath, variableName, recordFromFields(variable, fields));
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
        Map<String, Object> current = variable.value()
                .filter(value -> value.rowCount() > 0)
                .map(DataRecord::firstRow)
                .orElse(Map.of());
        Map<String, Object> provided = fields != null ? fields : Map.of();
        Map<String, Object> coerced = new LinkedHashMap<>();
        for (com.ispf.core.model.FieldDefinition field : variable.schema().fields()) {
            if (provided.containsKey(field.name())) {
                coerced.put(field.name(), ScriptFieldCoercion.coerce(field, provided.get(field.name())));
            } else if (current.containsKey(field.name())) {
                coerced.put(field.name(), current.get(field.name()));
            }
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

    /**
     * Writes only fields present in {@code fields}. Omitted fields keep the current row.
     * Missing required fields stay missing so the schema rejects them instead of inventing defaults.
     */
    private static DataRecord recordFromFields(Variable variable, Map<String, Object> fields) {
        Map<String, Object> current = variable.value()
                .filter(value -> value.rowCount() > 0)
                .map(DataRecord::firstRow)
                .orElse(Map.of());
        Map<String, Object> provided = fields != null ? fields : Map.of();
        Map<String, Object> row = new LinkedHashMap<>();
        for (FieldDefinition field : variable.schema().fields()) {
            if (provided.containsKey(field.name())) {
                row.put(field.name(), ScriptFieldCoercion.coerce(field, provided.get(field.name())));
            } else if (current.containsKey(field.name())) {
                row.put(field.name(), current.get(field.name()));
            }
        }
        return DataRecord.single(variable.schema(), row);
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
