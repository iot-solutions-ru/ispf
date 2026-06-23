package com.ispf.expression;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindingStatePortTest {

    private static final DataSchema DOUBLE_VALUE = DataSchema.builder("doubleValue")
            .field("value", FieldType.DOUBLE)
            .build();

    private static final DataSchema BOOL_VALUE = DataSchema.builder("boolValue")
            .field("value", FieldType.BOOLEAN)
            .build();

    @AfterEach
    void resetPort() {
        BindingTestSupport.clearRegistered();
        PlatformBindingRegistry.setBindingStatePort(new InMemoryBindingStatePort());
        PlatformBindingRegistry.clearStateForTests();
    }

    @BeforeEach
    void clearBindings() {
        BindingTestSupport.clearRegistered();
    }

    @Test
    void hysteresisLatchSurvivesPortReload() {
        PlatformObject node = sensorWithGauge(85.0);
        node.addVariable(BindingTestSupport.binding("alarm", BOOL_VALUE, "hysteresis(gauge, 80, 70)"));

        BindingTestSupport.evaluateRegistered(node);
        assertEquals(true, readBool(node, "alarm"));

        InMemoryBindingStatePort reloaded = new InMemoryBindingStatePort();
        String key = BindingSourceHelper.stateKey(node, "alarm");
        reloaded.putBoolean(key, true);
        PlatformBindingRegistry.setBindingStatePort(reloaded);

        node.getVariable("gauge").orElseThrow().setComputedValue(
                DataRecord.single(DOUBLE_VALUE, Map.of("value", 75.0))
        );
        BindingTestSupport.evaluateRegistered(node);
        assertEquals(true, readBool(node, "alarm"));
    }

    private static PlatformObject sensorWithGauge(double value) {
        PlatformObject node = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.devices.bind-state-test",
                ObjectType.DEVICE,
                "Bind state test",
                "",
                null
        );
        node.addVariable(new Variable(
                "gauge",
                DOUBLE_VALUE,
                true,
                false,
                DataRecord.single(DOUBLE_VALUE, Map.of("value", value))
        ));
        return node;
    }

    private static boolean readBool(PlatformObject node, String name) {
        return (Boolean) node.getVariable(name).orElseThrow().value().orElseThrow().firstRow().get("value");
    }
}
