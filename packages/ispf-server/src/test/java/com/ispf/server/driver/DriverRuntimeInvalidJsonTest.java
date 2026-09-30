package com.ispf.server.driver;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class DriverRuntimeInvalidJsonTest {

    private static final String DEVICE = "root.platform.devices.driver-invalid-json-test";

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private DeviceProvisioningService deviceProvisioningService;

    @Autowired
    private DriverRuntimeService driverRuntimeService;

    @AfterEach
    void cleanup() {
        try {
            driverRuntimeService.stopIfRunning(DEVICE);
        } catch (Exception ignored) {
            // best effort
        }
        try {
            objectManager.delete(DEVICE);
        } catch (Exception ignored) {
            // best effort
        }
    }

    @Test
    void invalidDriverConfigJsonFailsStartAndMarksError() {
        provisionDevice();
        setDriverJson("driverConfigJson", "{broken");

        assertThatThrownBy(() -> driverRuntimeService.start(DEVICE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Driver configuration JSON is not parseable");

        assertThat(driverRuntimeService.isActiveLocally(DEVICE)).isFalse();
        DriverRuntimeService.DriverRuntimeStatus status = driverRuntimeService.status(DEVICE).orElseThrow();
        assertThat(status.status()).isEqualTo("ERROR");
        assertThat(status.lastError()).contains("Driver configuration JSON is not parseable");
    }

    @Test
    void invalidDriverPointMappingsJsonFailsStartAndMarksError() {
        provisionDevice();
        setDriverJson("driverPointMappingsJson", "{broken");

        assertThatThrownBy(() -> driverRuntimeService.start(DEVICE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Driver point mappings JSON is not parseable");

        assertThat(driverRuntimeService.isActiveLocally(DEVICE)).isFalse();
        DriverRuntimeService.DriverRuntimeStatus status = driverRuntimeService.status(DEVICE).orElseThrow();
        assertThat(status.status()).isEqualTo("ERROR");
        assertThat(status.lastError()).contains("Driver point mappings JSON is not parseable");
    }

    private void provisionDevice() {
        objectManager.create(
                "root.platform.devices",
                "driver-invalid-json-test",
                ObjectType.DEVICE,
                "Driver invalid JSON test",
                null,
                null
        );
        deviceProvisioningService.provisionDriver(DEVICE, "virtual", 2000, false);
    }

    private void setDriverJson(String variableName, String value) {
        // Seed as if a legacy broken value was already stored: setSystemVariableValue would reject it now.
        DataRecord record = DataRecord.single(
                DataSchema.builder("stringValue").field("value", FieldType.STRING).build(),
                Map.of("value", value)
        );
        objectManager.require(DEVICE).getVariable(variableName).orElseThrow().setComputedValue(record);
    }
}
