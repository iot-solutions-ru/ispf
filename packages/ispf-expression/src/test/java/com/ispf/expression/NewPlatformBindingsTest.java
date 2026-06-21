package com.ispf.expression;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NewPlatformBindingsTest {

    private static final DataSchema DOUBLE_VALUE = DataSchema.builder("doubleValue")
            .field("value", FieldType.DOUBLE)
            .build();

    private static final DataSchema BOOL_VALUE = DataSchema.builder("boolValue")
            .field("value", FieldType.BOOLEAN)
            .build();

    private static final DataSchema TEMPERATURE = DataSchema.builder("temperature")
            .field("value", FieldType.DOUBLE)
            .field("unit", FieldType.STRING)
            .build();

    @BeforeEach
    void resetState() {
        PlatformBindingRegistry.clearStateForTests();
    }

    @Test
    void rateComputesDeltaOverTime() throws InterruptedException {
        PlatformObject node = sensorWithSource("gauge", 100.0);
        node.addVariable(binding("rateOut", DOUBLE_VALUE, "rate(gauge)"));

        BindingEvaluator evaluator = new BindingEvaluator();
        evaluator.evaluateBindingsReturningChanges(node);

        Thread.sleep(600);
        node.getVariable("gauge").orElseThrow().setComputedValue(
                DataRecord.single(DOUBLE_VALUE, Map.of("value", 250.0))
        );

        evaluator.evaluateBindingsReturningChanges(node);
        double rate = (Double) node.getVariable("rateOut").orElseThrow().value().orElseThrow().firstRow().get("value");
        assertTrue(rate > 200.0 && rate < 300.0, "rate was " + rate);
    }

    @Test
    void counterDeltaHandlesWrapWithoutTimeDivision() {
        PlatformObject node = sensorWithSource("counter", 1000.0);
        node.addVariable(binding("deltaOut", DOUBLE_VALUE, "counterDelta(counter)"));

        BindingEvaluator evaluator = new BindingEvaluator();
        evaluator.evaluateBindingsReturningChanges(node);

        node.getVariable("counter").orElseThrow().setComputedValue(
                DataRecord.single(DOUBLE_VALUE, Map.of("value", 1500.0))
        );
        evaluator.evaluateBindingsReturningChanges(node);

        assertEquals(500.0, node.getVariable("deltaOut").orElseThrow().value().orElseThrow().firstRow().get("value"));
    }

    @Test
    void movingAvgAveragesWithinWindow() {
        PlatformObject node = sensorWithSource("gauge", 10.0);
        node.addVariable(binding("avgOut", DOUBLE_VALUE, "movingAvg(gauge, 60)"));

        BindingEvaluator evaluator = new BindingEvaluator();
        evaluator.evaluateBindingsReturningChanges(node);
        assertEquals(10.0, node.getVariable("avgOut").orElseThrow().value().orElseThrow().firstRow().get("value"));

        node.getVariable("gauge").orElseThrow().setComputedValue(
                DataRecord.single(DOUBLE_VALUE, Map.of("value", 20.0))
        );
        evaluator.evaluateBindingsReturningChanges(node);
        assertEquals(15.0, node.getVariable("avgOut").orElseThrow().value().orElseThrow().firstRow().get("value"));
    }

    @Test
    void movingMinAndMaxTrackWindow() {
        PlatformObject node = sensorWithSource("gauge", 50.0);
        node.addVariable(binding("minOut", DOUBLE_VALUE, "movingMin(gauge, 60)"));
        node.addVariable(binding("maxOut", DOUBLE_VALUE, "movingMax(gauge, 60)"));

        BindingEvaluator evaluator = new BindingEvaluator();
        evaluator.evaluateBindingsReturningChanges(node);

        node.getVariable("gauge").orElseThrow().setComputedValue(
                DataRecord.single(DOUBLE_VALUE, Map.of("value", 10.0))
        );
        evaluator.evaluateBindingsReturningChanges(node);

        assertEquals(10.0, node.getVariable("minOut").orElseThrow().value().orElseThrow().firstRow().get("value"));
        assertEquals(50.0, node.getVariable("maxOut").orElseThrow().value().orElseThrow().firstRow().get("value"));
    }

    @Test
    void deadbandSuppressesSmallChanges() {
        PlatformObject node = sensorWithSource("gauge", 100.0);
        node.addVariable(binding("filtered", DOUBLE_VALUE, "deadband(gauge, 5)"));

        BindingEvaluator evaluator = new BindingEvaluator();
        List<String> first = evaluator.evaluateBindingsReturningChanges(node);
        assertTrue(first.contains("filtered"));

        node.getVariable("gauge").orElseThrow().setComputedValue(
                DataRecord.single(DOUBLE_VALUE, Map.of("value", 103.0))
        );
        List<String> second = evaluator.evaluateBindingsReturningChanges(node);
        assertFalse(second.contains("filtered"));

        node.getVariable("gauge").orElseThrow().setComputedValue(
                DataRecord.single(DOUBLE_VALUE, Map.of("value", 110.0))
        );
        List<String> third = evaluator.evaluateBindingsReturningChanges(node);
        assertTrue(third.contains("filtered"));
        assertEquals(110.0, node.getVariable("filtered").orElseThrow().value().orElseThrow().firstRow().get("value"));
    }

    @Test
    void hysteresisTogglesWithSeparateThresholds() {
        PlatformObject node = sensorWithSource("gauge", 50.0);
        node.addVariable(binding("alarm", BOOL_VALUE, "hysteresis(gauge, 80, 70)"));

        BindingEvaluator evaluator = new BindingEvaluator();
        evaluator.evaluateBindingsReturningChanges(node);
        assertEquals(false, node.getVariable("alarm").orElseThrow().value().orElseThrow().firstRow().get("value"));

        node.getVariable("gauge").orElseThrow().setComputedValue(
                DataRecord.single(DOUBLE_VALUE, Map.of("value", 85.0))
        );
        evaluator.evaluateBindingsReturningChanges(node);
        assertEquals(true, node.getVariable("alarm").orElseThrow().value().orElseThrow().firstRow().get("value"));

        node.getVariable("gauge").orElseThrow().setComputedValue(
                DataRecord.single(DOUBLE_VALUE, Map.of("value", 75.0))
        );
        evaluator.evaluateBindingsReturningChanges(node);
        assertEquals(true, node.getVariable("alarm").orElseThrow().value().orElseThrow().firstRow().get("value"));

        node.getVariable("gauge").orElseThrow().setComputedValue(
                DataRecord.single(DOUBLE_VALUE, Map.of("value", 65.0))
        );
        evaluator.evaluateBindingsReturningChanges(node);
        assertEquals(false, node.getVariable("alarm").orElseThrow().value().orElseThrow().firstRow().get("value"));
    }

    @Test
    void unitConvertCelsiusToFahrenheit() {
        PlatformObject node = sensorWithTemperature(0.0);
        node.addVariable(binding("fahrenheit", DOUBLE_VALUE, "unitConvert(temperature, C, F)"));

        BindingEvaluator evaluator = new BindingEvaluator();
        evaluator.evaluateBindingsReturningChanges(node);

        assertEquals(32.0, (Double) node.getVariable("fahrenheit").orElseThrow().value().orElseThrow().firstRow().get("value"), 0.01);
    }

    @Test
    void refAtReadsRemoteFieldViaContext() {
        PlatformObject local = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.devices.local",
                ObjectType.DEVICE,
                "local",
                "",
                null
        );
        local.addVariable(binding(
                "remoteTemp",
                DOUBLE_VALUE,
                "refAt(\"root.platform.devices.remote\", temperature)"
        ));

        BindingEvaluationContext context = new BindingEvaluationContext() {
            @Override
            public Optional<DataRecord> invokeFunction(String objectPath, String functionName, DataRecord input) {
                return Optional.empty();
            }

            @Override
            public Optional<Object> readRemoteField(String objectPath, String variableName, String field) {
                if ("root.platform.devices.remote".equals(objectPath) && "temperature".equals(variableName)) {
                    return Optional.of(42.0);
                }
                return Optional.empty();
            }
        };

        new BindingEvaluator().evaluateBindingsReturningChanges(local, context);
        assertEquals(42.0, local.getVariable("remoteTemp").orElseThrow().value().orElseThrow().firstRow().get("value"));
    }

    @Test
    void callFunctionInvokesViaContext() {
        PlatformObject node = sensorWithSource("inputVar", 10.0);
        node.addVariable(binding("output", DOUBLE_VALUE, "callFunction(doubleIt, inputVar)"));

        BindingEvaluationContext context = (objectPath, functionName, input) -> {
            if ("doubleIt".equals(functionName) && input.rowCount() > 0) {
                double value = ((Number) input.firstRow().get("value")).doubleValue();
                return Optional.of(DataRecord.single(DOUBLE_VALUE, Map.of("value", value * 2)));
            }
            return Optional.empty();
        };

        new BindingEvaluator().evaluateBindingsReturningChanges(node, context);
        assertEquals(20.0, node.getVariable("output").orElseThrow().value().orElseThrow().firstRow().get("value"));
    }

    @Test
    void callFunctionAtInvokesRemoteViaContext() {
        PlatformObject node = sensorWithSource("inputVar", 5.0);
        node.addVariable(binding(
                "output",
                DOUBLE_VALUE,
                "callFunctionAt(\"root.remote\", increment, inputVar)"
        ));

        BindingEvaluationContext context = (objectPath, functionName, input) -> {
            if ("root.remote".equals(objectPath) && "increment".equals(functionName)) {
                double value = ((Number) input.firstRow().get("value")).doubleValue();
                return Optional.of(DataRecord.single(DOUBLE_VALUE, Map.of("value", value + 1)));
            }
            return Optional.empty();
        };

        new BindingEvaluator().evaluateBindingsReturningChanges(node, context);
        assertEquals(6.0, node.getVariable("output").orElseThrow().value().orElseThrow().firstRow().get("value"));
    }

    private static PlatformObject sensorWithSource(String name, double value) {
        PlatformObject node = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.devices.sensor",
                ObjectType.DEVICE,
                "sensor",
                "",
                null
        );
        node.addVariable(new Variable(
                name,
                DOUBLE_VALUE,
                true,
                true,
                null,
                DataRecord.single(DOUBLE_VALUE, Map.of("value", value))
        ));
        return node;
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
                TEMPERATURE,
                true,
                true,
                null,
                DataRecord.single(TEMPERATURE, Map.of("value", value, "unit", "C"))
        ));
        return node;
    }

    private static Variable binding(String name, DataSchema schema, String expression) {
        return new Variable(
                name,
                schema,
                true,
                false,
                expression,
                DataRecord.single(schema, Map.of("value", schema.fields().getFirst().type() == FieldType.BOOLEAN ? false : 0.0))
        );
    }
}
