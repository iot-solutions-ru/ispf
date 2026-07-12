package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.driver.DriverBinding;
import com.ispf.server.driver.DriverRuntimeService;
import com.ispf.server.driver.TelemetryPublishMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ObjectManagerIngressFastPathTest {

    private static final String DEVICE = "root.platform.devices.demo-sensor-01";
    private static final DataSchema TEMPERATURE = DataSchema.builder("temperature")
            .field("value", FieldType.DOUBLE)
            .field("unit", FieldType.STRING)
            .build();
    private static final DataSchema MQTT_PAYLOAD = DataSchema.builder("mqttPayload")
            .field("raw", FieldType.STRING)
            .build();

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private DriverRuntimeService driverRuntimeService;

    @Test
    void skipsRamUpdateForHistorianOnlyTelemetryOnlyIngress() {
        driverRuntimeService.stop(DEVICE);
        objectManager.setVariableValue(
                DEVICE,
                "temperature",
                DataRecord.single(TEMPERATURE, Map.of("value", 10.0, "unit", "C"))
        );
        driverRuntimeService.configure(
                DEVICE,
                DriverBinding.of(
                        "virtual",
                        1000,
                        Map.of("telemetryPublishMode", "TELEMETRY_ONLY"),
                        Map.of(),
                        TelemetryPublishMode.TELEMETRY_ONLY,
                        1
                )
        );

        objectManager.setDriverTelemetryValue(
                DEVICE,
                "temperature",
                DataRecord.single(MQTT_PAYLOAD, Map.of("raw", "99.9")),
                Instant.now()
        );

        var variable = objectManager.require(DEVICE).getVariable("temperature").orElseThrow();
        assertThat(variable.value().orElseThrow().firstRow().get("value")).isEqualTo(10.0);
    }

    @Test
    void skipsRamUpdateWhenVariableOverrideIsTelemetryOnly() {
        driverRuntimeService.stop(DEVICE);
        objectManager.setVariableValue(
                DEVICE,
                "temperature",
                DataRecord.single(TEMPERATURE, Map.of("value", 10.0, "unit", "C"))
        );
        objectManager.updateVariableHistory(DEVICE, "temperature", true, null, "TELEMETRY_ONLY");
        driverRuntimeService.configure(
                DEVICE,
                DriverBinding.of(
                        "virtual",
                        1000,
                        Map.of("telemetryPublishMode", "FULL"),
                        Map.of(),
                        TelemetryPublishMode.FULL,
                        1
                )
        );

        objectManager.setDriverTelemetryValue(
                DEVICE,
                "temperature",
                DataRecord.single(MQTT_PAYLOAD, Map.of("raw", "99.9")),
                Instant.now()
        );

        var variable = objectManager.require(DEVICE).getVariable("temperature").orElseThrow();
        assertThat(variable.value().orElseThrow().firstRow().get("value")).isEqualTo(10.0);
    }
}
