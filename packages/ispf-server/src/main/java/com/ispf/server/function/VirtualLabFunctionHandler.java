package com.ispf.server.function;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldDefinition;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.event.EventService;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class VirtualLabFunctionHandler implements FunctionHandler {

    private static final Set<String> SUPPORTED = Set.of(
            "calculate",
            "fireEvent1",
            "fireEvent2",
            "appendTableRow"
    );

    private static final DataSchema RESULT_SCHEMA = DataSchema.builder("functionResult")
            .field("success", FieldType.BOOLEAN)
            .field("message", FieldType.STRING)
            .build();

    private static final DataSchema CALCULATE_OUTPUT_SCHEMA = DataSchema.builder("calculateOutput")
            .field("result", FieldType.DOUBLE)
            .build();

    private final ObjectManager objectManager;
    private final EventService eventService;

    public VirtualLabFunctionHandler(ObjectManager objectManager, EventService eventService) {
        this.objectManager = objectManager;
        this.eventService = eventService;
    }

    @Override
    public boolean supports(String objectPath, String functionName) {
        if (!SUPPORTED.contains(functionName)) {
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
        return switch (functionName) {
            case "calculate" -> calculate(input);
            case "fireEvent1" -> fireEvent(objectPath, "event1", input);
            case "fireEvent2" -> fireEvent(objectPath, "event2", input);
            case "appendTableRow" -> appendTableRow(objectPath, input);
            default -> throw new IllegalArgumentException("Unsupported function: " + functionName);
        };
    }

    private DataRecord calculate(DataRecord input) {
        Map<String, Object> row = input.firstRow();
        double inputA = numberValue(row.get("inputA"));
        double inputB = numberValue(row.get("inputB"));
        return DataRecord.single(CALCULATE_OUTPUT_SCHEMA, Map.of("result", inputA + inputB));
    }

    private DataRecord fireEvent(String objectPath, String eventName, DataRecord input) {
        Map<String, Object> row = input.firstRow();
        DataRecord payload = DataRecord.single(
                eventPayloadSchema(objectPath, eventName),
                Map.of(
                        "int", intValue(row.get("int")),
                        "string", stringValue(row.get("string"))
                )
        );
        eventService.fire(objectPath, eventName, payload);
        return DataRecord.single(RESULT_SCHEMA, Map.of("success", true, "message", eventName + " fired"));
    }

    private DataRecord appendTableRow(String objectPath, DataRecord input) {
        PlatformObject node = objectManager.require(objectPath);
        Variable tableVariable = node.getVariable("table")
                .orElseThrow(() -> new IllegalStateException("table variable not found"));
        DataRecord current = tableVariable.value().orElse(DataRecord.empty(tableVariable.schema()));
        String listField = tableVariable.schema().fields().stream()
                .filter(field -> field.type() == FieldType.RECORD_LIST)
                .map(FieldDefinition::name)
                .findFirst()
                .orElse("rows");

        List<Map<String, Object>> rows = new ArrayList<>();
        if (current.rowCount() > 0) {
            Object existingRows = current.firstRow().get(listField);
            if (existingRows instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        Map<String, Object> copy = new LinkedHashMap<>();
                        map.forEach((key, value) -> copy.put(String.valueOf(key), value));
                        rows.add(copy);
                    }
                }
            }
        }

        Map<String, Object> row = input.firstRow();
        rows.add(Map.of(
                "int", intValue(row.get("int")),
                "string", stringValue(row.get("string"))
        ));

        objectManager.setVariableValue(
                objectPath,
                "table",
                DataRecord.single(tableVariable.schema(), Map.of(listField, rows))
        );
        return DataRecord.single(RESULT_SCHEMA, Map.of(
                "success", true,
                "message", "Row appended (" + rows.size() + " total)"
        ));
    }

    private DataSchema eventPayloadSchema(String objectPath, String eventName) {
        return objectManager.require(objectPath).events().get(eventName).payloadSchema();
    }

    private static double numberValue(Object raw) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        if (raw == null) {
            return 0.0;
        }
        return Double.parseDouble(raw.toString());
    }

    private static int intValue(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw == null) {
            return 0;
        }
        return Integer.parseInt(raw.toString());
    }

    private static String stringValue(Object raw) {
        return raw == null ? "" : raw.toString();
    }
}
