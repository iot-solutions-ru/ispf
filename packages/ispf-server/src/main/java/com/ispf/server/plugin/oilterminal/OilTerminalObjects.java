package com.ispf.server.plugin.oilterminal;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.oilterminal.OilTerminalConstants;
import com.ispf.server.object.ObjectManager;

import java.util.Map;
import java.util.Optional;

final class OilTerminalObjects {

    static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    static final DataSchema DOUBLE_VALUE = DataSchema.builder("doubleValue")
            .field("value", FieldType.DOUBLE)
            .build();

    static final DataSchema BOOLEAN_VALUE = DataSchema.builder("booleanValue")
            .field("value", FieldType.BOOLEAN)
            .build();

    static final DataSchema FUNCTION_RESULT = DataSchema.builder("functionResult")
            .field("success", FieldType.BOOLEAN)
            .field("message", FieldType.STRING)
            .build();

    private OilTerminalObjects() {
    }

    static boolean isDispatchOrder(PlatformObject node) {
        return node.path().startsWith(OilTerminalConstants.ORDERS + ".")
                && node.functions().containsKey("assign");
    }

    static boolean isOilTank(PlatformObject node) {
        return node.path().startsWith(OilTerminalConstants.TANKS + ".")
                && node.getVariable("levelM3").isPresent();
    }

    static boolean isOilRack(PlatformObject node) {
        return node.path().startsWith(OilTerminalConstants.RACKS + ".")
                && node.getVariable("totalizerL").isPresent();
    }

    static boolean isOilSample(PlatformObject node) {
        return node.path().startsWith(OilTerminalConstants.SAMPLES + ".")
                && node.functions().containsKey("approve");
    }

    static Optional<String> stringValue(ObjectManager objectManager, String path, String variable) {
        return objectManager.tree().findByPath(path)
                .flatMap(node -> node.getVariable(variable))
                .flatMap(v -> v.value())
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString);
    }

    static Optional<Double> doubleValue(ObjectManager objectManager, String path, String variable) {
        return objectManager.tree().findByPath(path)
                .flatMap(node -> node.getVariable(variable))
                .flatMap(v -> v.value())
                .map(record -> record.firstRow().get("value"))
                .map(value -> ((Number) value).doubleValue());
    }

    static Optional<Boolean> booleanValue(ObjectManager objectManager, String path, String variable) {
        return objectManager.tree().findByPath(path)
                .flatMap(node -> node.getVariable(variable))
                .flatMap(v -> v.value())
                .map(record -> record.firstRow().get("value"))
                .map(value -> value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value)));
    }

    static void setString(ObjectManager objectManager, String path, String variable, String value) {
        objectManager.setVariableValue(path, variable, DataRecord.single(STRING_VALUE, Map.of("value", value)));
    }

    static void setDouble(ObjectManager objectManager, String path, String variable, double value) {
        objectManager.setVariableValue(path, variable, DataRecord.single(DOUBLE_VALUE, Map.of("value", value)));
    }

    static void setBoolean(ObjectManager objectManager, String path, String variable, boolean value) {
        objectManager.setVariableValue(path, variable, DataRecord.single(BOOLEAN_VALUE, Map.of("value", value)));
    }

    static DataRecord success(String message) {
        return DataRecord.single(FUNCTION_RESULT, Map.of("success", true, "message", message));
    }

    static DataRecord failure(String message) {
        return DataRecord.single(FUNCTION_RESULT, Map.of("success", false, "message", message));
    }
}
