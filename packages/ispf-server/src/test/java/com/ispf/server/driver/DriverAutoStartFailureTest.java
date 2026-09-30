package com.ispf.server.driver;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.Variable;
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

@SpringBootTest
@ActiveProfiles("test")
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class DriverAutoStartFailureTest {

    private static final String DEVICE = "root.platform.devices.driver-autostart-failure-test";

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
    void autoStartFailureMarksDeviceError() {
        objectManager.create(
                "root.platform.devices",
                "driver-autostart-failure-test",
                ObjectType.DEVICE,
                "Driver auto-start failure test",
                null,
                null
        );
        deviceProvisioningService.provisionDriver(DEVICE, "virtual", 2000, true);
        // Seed an unknown driver id as if a legacy value was stored directly on the device.
        setDriverVariable("driverId", "no-such-driver");

        driverRuntimeService.startConfiguredDrivers();

        assertThat(driverRuntimeService.isActiveLocally(DEVICE)).isFalse();
        assertThat(readDriverVariable("driverStatus")).isEqualTo("ERROR");
    }

    private void setDriverVariable(String variableName, String value) {
        DataRecord record = DataRecord.single(
                DataSchema.builder("stringValue").field("value", FieldType.STRING).build(),
                Map.of("value", value)
        );
        objectManager.require(DEVICE).getVariable(variableName).orElseThrow().setComputedValue(record);
    }

    private String readDriverVariable(String variableName) {
        return objectManager.require(DEVICE).getVariable(variableName)
                .flatMap(Variable::value)
                .flatMap(record -> record.rows().stream().findFirst())
                .map(row -> row.get("value"))
                .map(Object::toString)
                .orElse(null);
    }
}
