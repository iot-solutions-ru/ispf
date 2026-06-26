package com.ispf.server.driver;

import com.ispf.core.object.ObjectType;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.model.ModelApplicationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
@ActiveProfiles("test")
class DeviceProvisioningMqttTest {

    private static final String DEVICE_PATH = "root.platform.devices.mqtt-provision-test";

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private ModelApplicationService modelApplicationService;

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
    void provisionsMqttAfterRelativeModelsWithoutDriverConflict() {
        objectManager.create(
                "root.platform.devices",
                "mqtt-provision-test",
                ObjectType.DEVICE,
                "MQTT provision test",
                null,
                null
        );
        modelApplicationService.applyRelativeModelsWithRules(DEVICE_PATH);

        assertThat(driverRuntimeService.debugReadDriverId(DEVICE_PATH)).isEmpty();

        assertThatCode(() -> deviceProvisioningService.provisionDriver(DEVICE_PATH, "mqtt", 5000, false))
                .doesNotThrowAnyException();

        assertThat(driverRuntimeService.debugReadDriverId(DEVICE_PATH)).contains("mqtt");
        assertThat(driverRuntimeService.status(DEVICE_PATH).orElseThrow().driverId()).isEqualTo("mqtt");
    }
}
