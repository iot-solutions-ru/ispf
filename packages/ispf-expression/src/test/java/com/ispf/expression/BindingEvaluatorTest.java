package com.ispf.expression;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.Variable;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    private final BindingExpressionEvaluator evaluator = new BindingExpressionEvaluator();

    @BeforeEach
    void resetBindingState() {
        PlatformBindingRegistry.clearStateForTests();
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
                DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 1000.0, "raw", "1000", "type", "Counter32"))
        );
        counter.setComputedValue(DataRecord.single(
                SNMP_NUMERIC_SCHEMA,
                Map.of("value", 1000.0, "raw", "1000", "type", "Counter32")
        ));
        node.addVariable(counter);
        Variable rateVar = new Variable(
                "ifInOctetsRate",
                SNMP_NUMERIC_SCHEMA,
                true,
                false,
                DataRecord.single(SNMP_NUMERIC_SCHEMA, Map.of("value", 0.0, "raw", "", "type", ""))
        );
        node.addVariable(rateVar);

        evaluator.evaluate(
                node,
                "ifInOctetsRate",
                "counterRate(ifInOctets)",
                SNMP_NUMERIC_SCHEMA,
                BindingEvaluationContext.NONE
        );

        Thread.sleep(600);
        counter.setComputedValue(DataRecord.single(
                SNMP_NUMERIC_SCHEMA,
                Map.of("value", 2000.0, "raw", "2000", "type", "Counter32")
        ));

        var computed = evaluator.evaluate(
                node,
                "ifInOctetsRate",
                "counterRate(ifInOctets)",
                SNMP_NUMERIC_SCHEMA,
                BindingEvaluationContext.NONE
        );
        assertTrue(computed.isPresent());
        rateVar.setComputedValue(computed.get());

        Map<String, Object> row = rateVar.value().orElseThrow().firstRow();
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
                DataRecord.single(temperatureSchema, Map.of("value", 95.0, "unit", "C"))
        ));
        node.addVariable(new Variable(
                "threshold",
                thresholdSchema,
                true,
                true,
                DataRecord.single(thresholdSchema, Map.of("value", 80.0))
        ));
        Variable alarm = new Variable(
                "alarmActive",
                alarmSchema,
                true,
                false,
                DataRecord.single(alarmSchema, Map.of("value", false))
        );
        node.addVariable(alarm);

        var computed = evaluator.evaluate(
                node,
                "alarmActive",
                "self.temperature.value > self.threshold.value",
                alarmSchema,
                BindingEvaluationContext.NONE
        );
        assertTrue(computed.isPresent());
        alarm.setComputedValue(computed.get());

        assertEquals(
                true,
                alarm.value().orElseThrow().firstRow().get("value")
        );
    }
}
