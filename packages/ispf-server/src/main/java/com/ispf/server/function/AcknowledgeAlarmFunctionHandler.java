package com.ispf.server.function;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AcknowledgeAlarmFunctionHandler implements FunctionHandler {

    private static final DataSchema RESULT_SCHEMA = DataSchema.builder("functionResult")
            .field("success", FieldType.BOOLEAN)
            .field("message", FieldType.STRING)
            .build();

    private static final DataSchema ACK_SCHEMA = DataSchema.builder("alarmAcknowledged")
            .field("value", FieldType.BOOLEAN)
            .build();

    private final ObjectManager objectManager;

    public AcknowledgeAlarmFunctionHandler(ObjectManager objectManager) {
        this.objectManager = objectManager;
    }

    @Override
    public boolean supports(String objectPath, String functionName) {
        if (!"acknowledgeAlarm".equals(functionName)) {
            return false;
        }
        PlatformObject node = objectManager.tree().findByPath(objectPath).orElse(null);
        return node != null && node.functions().containsKey(functionName);
    }

    @Override
    public DataRecord invoke(String objectPath, String functionName, DataRecord input) {
        PlatformObject node = objectManager.require(objectPath);
        FunctionDescriptor descriptor = node.functions().get(functionName);
        if (descriptor == null) {
            throw new IllegalArgumentException("Unknown function: " + functionName);
        }

        objectManager.setVariableValue(
                objectPath,
                "alarmAcknowledged",
                DataRecord.single(ACK_SCHEMA, Map.of("value", true))
        );

        return DataRecord.single(RESULT_SCHEMA, Map.of(
                "success", true,
                "message", "Alarm acknowledged"
        ));
    }
}
