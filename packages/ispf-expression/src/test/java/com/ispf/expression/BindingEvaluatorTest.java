package com.ispf.expression;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.ObjectType;
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

class BindingEvaluatorTest {

    private static final DataSchema SNMP_NUMERIC_SCHEMA = DataSchema.builder("snmpNumeric")
            .field("value", FieldType.DOUBLE)
            .field("raw", FieldType.STRING)
            .field("type", FieldType.STRING)
            .build();

    @BeforeEach
    void resetCounterRateState() {
        CounterRateBinding.clearStateForTests();
    }

    @Test
    void counterRateBindingMapsScalarIntoSnmpNumericSchema() throws InterruptedException {
        PlatformObject node = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.devices.snmp-localhost",
                ObjectType.DEVICE,
                "snmp-localhost",
                "",
                null
        );
        Variable counter = new Variable(
                "ifInOctets",
                SNMP_NUMERIC_SCHEMA,
                true,
                true,
                null,
                DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 1000.0, "raw", "1000", "type", "Counter32"))
        );
        counter.setComputedValue(DataRecord.single(
                SNMP_NUMERIC_SCHEMA,
                Map.of("value", 1000.0, "raw", "1000", "type", "Counter32")
        ));
        node.addVariable(counter);
        node.addVariable(new Variable(
                "ifInOctetsRate",
                SNMP_NUMERIC_SCHEMA,
                true,
                false,
                "counterRate(ifInOctets)",
                DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
        ));

        BindingEvaluator evaluator = new BindingEvaluator();
        evaluator.evaluateBindingsReturningChanges(node);

        Thread.sleep(600);
        counter.setComputedValue(DataRecord.single(
                SNMP_NUMERIC_SCHEMA,
                Map.of("value", 2000.0, "raw", "2000", "type", "Counter32")
        ));

        List<String> changed = evaluator.evaluateBindingsReturningChanges(node);

        assertTrue(changed.contains("ifInOctetsRate"));
        Map<String, Object> row = node.getVariable("ifInOctetsRate").orElseThrow()
                .value().orElseThrow().firstRow();
        double rate = (Double) row.get("value");
        assertTrue(rate > 1000.0, "rate should reflect 1000 octet delta over ~600ms, was " + rate);
        assertEquals(String.valueOf(rate), row.get("raw"));
    }

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
