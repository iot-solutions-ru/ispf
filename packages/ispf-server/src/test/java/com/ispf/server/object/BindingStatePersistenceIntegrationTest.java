package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.Variable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class BindingStatePersistenceIntegrationTest {

    private static final String DEVICE = "root.platform.devices.demo-sensor-01";
    private static final DataSchema TEMPERATURE = DataSchema.builder("temperature")
            .field("value", FieldType.DOUBLE)
            .field("unit", FieldType.STRING)
            .build();
    private static final DataSchema BOOL_VALUE = DataSchema.builder("boolValue")
            .field("value", FieldType.BOOLEAN)
            .build();

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private ObjectBindingStatePort bindingStatePort;

    private String alarmVariable;

    @AfterEach
    void cleanup() {
        if (alarmVariable != null) {
            objectManager.deleteVariable(DEVICE, alarmVariable);
            alarmVariable = null;
        }
    }

    @Test
    void hysteresisLatchPersistsInBindingStateVariable() {
        alarmVariable = "bindPersistAlarm" + System.nanoTime();
        objectManager.createVariable(
                DEVICE,
                alarmVariable,
                BOOL_VALUE,
                true,
                false,
                "hysteresis(temperature, 80, 70)",
                null,
                false,
                null
        );

        objectManager.setVariableValue(
                DEVICE,
                "temperature",
                DataRecord.single(TEMPERATURE, Map.of("value", 85.0, "unit", "C"))
        );
        assertTrue(readBool(alarmVariable));

        bindingStatePort.invalidateCache(DEVICE);

        objectManager.setVariableValue(
                DEVICE,
                "temperature",
                DataRecord.single(TEMPERATURE, Map.of("value", 75.0, "unit", "C"))
        );
        assertTrue(readBool(alarmVariable));

        assertTrue(objectManager.require(DEVICE).getVariable(BindingStateVariables.BINDING_STATE).isPresent());
    }

    @Test
    void counterRateStatePersistsAcrossCacheReload() throws InterruptedException {
        String counterName = "bindPersistCounter" + System.nanoTime();
        String rateName = "bindPersistRate" + System.nanoTime();
        DataSchema counterSchema = DataSchema.builder("counter32")
                .field("value", FieldType.DOUBLE)
                .build();
        try {
            objectManager.createVariable(
                    DEVICE,
                    counterName,
                    counterSchema,
                    true,
                    true,
                    null,
                    DataRecord.single(counterSchema, Map.of("value", 1000.0)),
                    false,
                    null
            );
            objectManager.createVariable(
                    DEVICE,
                    rateName,
                    DataSchema.builder("rate")
                            .field("value", FieldType.DOUBLE)
                            .build(),
                    true,
                    false,
                    "counterRate(" + counterName + ")",
                    null,
                    false,
                    null
            );

            objectManager.setVariableValue(
                    DEVICE,
                    counterName,
                    DataRecord.single(counterSchema, Map.of("value", 2000.0))
            );
            Thread.sleep(600);
            objectManager.setVariableValue(
                    DEVICE,
                    counterName,
                    DataRecord.single(counterSchema, Map.of("value", 3000.0))
            );

            bindingStatePort.invalidateCache(DEVICE);

            Thread.sleep(600);
            objectManager.setVariableValue(
                    DEVICE,
                    counterName,
                    DataRecord.single(counterSchema, Map.of("value", 4000.0))
            );
            Double rate = readDouble(rateName);
            assertTrue(rate != null && rate > 0, "rate should be computed after cache reload");
        } finally {
            objectManager.deleteVariable(DEVICE, rateName);
            objectManager.deleteVariable(DEVICE, counterName);
        }
    }

    private boolean readBool(String variableName) {
        return (Boolean) objectManager.require(DEVICE)
                .getVariable(variableName)
                .flatMap(Variable::value)
                .orElseThrow()
                .firstRow()
                .get("value");
    }

    private Double readDouble(String variableName) {
        Object value = objectManager.require(DEVICE)
                .getVariable(variableName)
                .flatMap(Variable::value)
                .orElseThrow()
                .firstRow()
                .get("value");
        return value instanceof Number number ? number.doubleValue() : null;
    }
}
