package com.ispf.expression;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformBindingsTest {

    private static final DataSchema TEMPERATURE_SCHEMA = DataSchema.builder("temperature")
            .field("value", FieldType.DOUBLE)
            .field("unit", FieldType.STRING)
            .build();

    private static final DataSchema DOUBLE_VALUE_SCHEMA = DataSchema.builder("doubleValue")
            .field("value", FieldType.DOUBLE)
            .build();

    private static final DataSchema STRING_VALUE_SCHEMA = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    @BeforeEach
    void resetState() {
        PlatformBindingRegistry.clearStateForTests();
    }

    @Test
    void selectFieldReturnsNamedField() {
        PlatformObject node = sensorWithTemperature(22.5);
        node.addVariable(bindingVariable("temperatureUnit", STRING_VALUE_SCHEMA, "selectField(temperature, unit)"));

        BindingEvaluator evaluator = new BindingEvaluator();
        List<String> changed = evaluator.evaluateBindingsReturningChanges(node);

        assertTrue(changed.contains("temperatureUnit"));
        assertEquals("C", node.getVariable("temperatureUnit").orElseThrow().value().orElseThrow().firstRow().get("value"));
    }

    @Test
    void selectFieldDefaultsToValueField() {
        PlatformObject node = sensorWithTemperature(42.0);
        node.addVariable(bindingVariable("temperatureCopy", DOUBLE_VALUE_SCHEMA, "selectField(temperature)"));

        BindingEvaluator evaluator = new BindingEvaluator();
        evaluator.evaluateBindingsReturningChanges(node);

        assertEquals(42.0, node.getVariable("temperatureCopy").orElseThrow().value().orElseThrow().firstRow().get("value"));
    }

    @Test
    void scaleMapsLinearRange() {
        PlatformObject node = sensorWithTemperature(22.5);
        node.addVariable(bindingVariable(
                "temperaturePercent",
                DOUBLE_VALUE_SCHEMA,
                "scale(temperature, -20, 50, 0, 100)"
        ));

        BindingEvaluator evaluator = new BindingEvaluator();
        evaluator.evaluateBindingsReturningChanges(node);

        double percent = (Double) node.getVariable("temperaturePercent").orElseThrow()
                .value().orElseThrow().firstRow().get("value");
        assertEquals(60.714285714285715, percent, 0.001);
    }

    @Test
    void clampLimitsNumericValue() {
        PlatformObject node = sensorWithTemperature(95.0);
        node.addVariable(bindingVariable("temperatureClamped", DOUBLE_VALUE_SCHEMA, "clamp(temperature, 0, 50)"));

        BindingEvaluator evaluator = new BindingEvaluator();
        evaluator.evaluateBindingsReturningChanges(node);

        assertEquals(50.0, node.getVariable("temperatureClamped").orElseThrow().value().orElseThrow().firstRow().get("value"));
    }

    @Test
    void formatProducesStringValue() {
        PlatformObject node = sensorWithTemperature(23.5);
        node.addVariable(bindingVariable(
                "temperatureLabel",
                STRING_VALUE_SCHEMA,
                "format(\"%.1f °C\", temperature)"
        ));

        BindingEvaluator evaluator = new BindingEvaluator();
        evaluator.evaluateBindingsReturningChanges(node);

        assertEquals("23.5 °C", node.getVariable("temperatureLabel").orElseThrow().value().orElseThrow().firstRow().get("value"));
    }

    @Test
    void deltaReturnsDifferenceFromPreviousSample() {
        PlatformObject node = sensorWithTemperature(100.0);
        node.addVariable(bindingVariable("temperatureDelta", DOUBLE_VALUE_SCHEMA, "delta(temperature)"));

        BindingEvaluator evaluator = new BindingEvaluator();
        evaluator.evaluateBindingsReturningChanges(node);

        assertTrue(node.getVariable("temperatureDelta").orElseThrow().value().isPresent());
        assertEquals(0.0, node.getVariable("temperatureDelta").orElseThrow().value().orElseThrow().firstRow().get("value"));

        node.getVariable("temperature").orElseThrow().setComputedValue(
                DataRecord.single(TEMPERATURE_SCHEMA, Map.of("value", 107.5, "unit", "C"))
        );

        List<String> changed = evaluator.evaluateBindingsReturningChanges(node);

        assertTrue(changed.contains("temperatureDelta"));
        assertEquals(7.5, node.getVariable("temperatureDelta").orElseThrow().value().orElseThrow().firstRow().get("value"));
    }

    private static PlatformObject sensorWithTemperature(double value) {
        PlatformObject node = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.devices.sensor",
                ObjectType.DEVICE,
                "sensor",
                "",
                null
        );
        node.addVariable(new Variable(
                "temperature",
                TEMPERATURE_SCHEMA,
                true,
                true,
                null,
                DataRecord.single(TEMPERATURE_SCHEMA, Map.of("value", value, "unit", "C"))
        ));
        return node;
    }

    private static Variable bindingVariable(String name, DataSchema schema, String expression) {
        Map<String, Object> defaults = schema.fieldCount() == 1 && schema.fields().getFirst().type() == FieldType.STRING
                ? Map.of("value", "")
                : Map.of("value", 0.0);
        return new Variable(name, schema, true, false, expression, DataRecord.single(schema, defaults));
    }
}
