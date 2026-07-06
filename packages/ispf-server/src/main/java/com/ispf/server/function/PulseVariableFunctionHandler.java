package com.ispf.server.function;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Generic SCADA command pulse: sets a boolean writable variable on the caller object.
 * Descriptor: {@code sourceType=pulse}, {@code sourceBody={"variable":"cmdStart"}}.
 */
@Component
public class PulseVariableFunctionHandler implements FunctionHandler {

    private static final DataSchema BOOL_VAL = DataSchema.builder("boolValue")
            .field("value", FieldType.BOOLEAN)
            .build();
    private static final DataSchema RESULT = DataSchema.builder("functionResult")
            .field("success", FieldType.BOOLEAN)
            .field("message", FieldType.STRING)
            .build();

    private final ObjectManager objectManager;
    private final ObjectMapper objectMapper;

    public PulseVariableFunctionHandler(ObjectManager objectManager, ObjectMapper objectMapper) {
        this.objectManager = objectManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String objectPath, String functionName) {
        PlatformObject node = objectManager.tree().findByPath(objectPath).orElse(null);
        if (node == null) {
            return false;
        }
        FunctionDescriptor descriptor = node.functions().get(functionName);
        return descriptor != null && descriptor.hasPulseBody();
    }

    @Override
    public DataRecord invoke(String objectPath, String functionName, DataRecord input) {
        PlatformObject node = objectManager.require(objectPath);
        FunctionDescriptor descriptor = node.functions().get(functionName);
        if (descriptor == null || !descriptor.hasPulseBody()) {
            throw new IllegalArgumentException("Unknown pulse function: " + functionName);
        }
        PulseConfig config = parseConfig(descriptor.sourceBody());
        String targetPath = config.objectPath() != null && !config.objectPath().isBlank()
                ? config.objectPath()
                : objectPath;
        objectManager.setVariableValue(
                targetPath,
                config.variable(),
                DataRecord.single(BOOL_VAL, Map.of("value", config.value()))
        );
        return DataRecord.single(RESULT, Map.of("success", true, "message", "Command sent"));
    }

    private PulseConfig parseConfig(String sourceBody) {
        try {
            JsonNode root = objectMapper.readTree(sourceBody);
            String variable = root.path("variable").asText("");
            if (variable.isBlank()) {
                throw new IllegalArgumentException("pulse sourceBody requires variable");
            }
            boolean value = root.has("value") ? root.path("value").asBoolean(true) : true;
            String objectPath = root.path("objectPath").asText(null);
            return new PulseConfig(variable, value, objectPath);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid pulse sourceBody: " + ex.getMessage(), ex);
        }
    }

    private record PulseConfig(String variable, boolean value, String objectPath) {
    }
}
