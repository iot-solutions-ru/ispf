package com.ispf.expression;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.Variable;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindingEvaluatorTest {

    @Test
    void evaluatesAlarmBinding() {
        DataSchema temperatureSchema = DataSchema.builder("temperature")
                .field("value", FieldType.DOUBLE)
                .field("unit", FieldType.STRING)
                .build();
        DataSchema thresholdSchema = DataSchema.builder("threshold")
                .field("value", FieldType.DOUBLE)
                .build();
        DataSchema alarmSchema = DataSchema.builder("alarmActive")
                .field("value", FieldType.BOOLEAN)
                .build();

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
                temperatureSchema,
                true,
                true,
                null,
                DataRecord.single(temperatureSchema, Map.of("value", 95.0, "unit", "C"))
        ));
        node.addVariable(new Variable(
                "threshold",
                thresholdSchema,
                true,
                true,
                null,
                DataRecord.single(thresholdSchema, Map.of("value", 80.0))
        ));
        node.addVariable(new Variable(
                "alarmActive",
                alarmSchema,
                true,
                false,
                "self.temperature.value > self.threshold.value",
                DataRecord.single(alarmSchema, Map.of("value", false))
        ));

        BindingEvaluator evaluator = new BindingEvaluator();
        List<String> changed = evaluator.evaluateBindingsReturningChanges(node);

        assertTrue(changed.contains("alarmActive"));
        assertEquals(
                true,
                node.getVariable("alarmActive").orElseThrow().value().orElseThrow().firstRow().get("value")
        );
    }
}
