package com.ispf.server.driver;

import com.ispf.core.object.ObjectType;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DriverRuntimeConfigureReadOnlyTest {

    private static final String DEVICE_PATH = "root.platform.devices.driver-readonly-config-test";

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private DeviceProvisioningService deviceProvisioningService;

    @Autowired
    private DriverRuntimeService driverRuntimeService;

    @AfterEach
    void cleanup() {
        try {
            driverRuntimeService.stopIfRunning(DEVICE_PATH);
        } catch (Exception ignored) {
            // best effort
        }
        try {
            objectManager.delete(DEVICE_PATH);
        } catch (Exception ignored) {
            // best effort
        }
    }

    @Test
    void reconfiguresPollWhenDriverIdVariableIsReadOnly() {
        objectManager.create(
                "root.platform.devices",
                "driver-readonly-config-test",
                ObjectType.DEVICE,
                "Driver read-only configure test",
                null,
                null
        );
        deviceProvisioningService.provisionDriver(DEVICE_PATH, "virtual", 2000, false);

        driverRuntimeService.configure(
                DEVICE_PATH,
                DriverBinding.of("virtual", 1000, Map.of("profile", "lab"), Map.of())
        );
        driverRuntimeService.start(DEVICE_PATH);

        DriverRuntimeService.DriverRuntimeStatus status = driverRuntimeService.status(DEVICE_PATH).orElseThrow();
        assertThat(status.pollIntervalMs()).isEqualTo(1000);
        assertThat(status.driverId()).isEqualTo("virtual");
    }
}
