package com.ispf.server.function;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.server.bootstrap.DemoFixtureBootstrap;
import com.ispf.server.driver.DeviceTelemetryPolicyService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.ObjectTemplateService;
import com.ispf.server.object.RuntimeTelemetryCoalescer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ispf.object-change.async-enabled=true",
        "ispf.object-change.split-lanes-enabled=true",
        "ispf.object-change.coalesce-telemetry-updates=false",
        "ispf.runtime-telemetry.enabled=true",
        "ispf.runtime-telemetry.coalesce-ms=10",
        "ispf.mqtt-gateway.ingress-dispatch-elastic-enabled=true",
        "ispf.mqtt-gateway.ingress-dispatch-threads-max=16"
})
class MeterMqttBurstIngressIntegrationTest {

    private static final String BUS = "root.platform.devices.mqtt-meter-bus-burst-test";
    private static final DataSchema INGRESS_SCHEMA = DataSchema.builder("mqttIngress")
            .field("topic", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .build();

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private ObjectTemplateService objectTemplateService;

    @Autowired
    private RuntimeTelemetryCoalescer telemetryCoalescer;

    @Autowired
    private DeviceTelemetryPolicyService telemetryPolicyService;

    @AfterEach
    void cleanup() {
        for (int i = 1; i <= 50; i++) {
            String path = "root.platform.instances.meter-" + String.format("%04d", i);
            objectManager.tree().findByPath(path).ifPresent(node -> objectManager.delete(path));
        }
        objectManager.tree().findByPath(BUS).ifPresent(node -> objectManager.delete(BUS));
    }

    @Test
    void burstIngressCreatesAllInstancesWhenPayloadLanesEnabled() throws Exception {
        ensureBusDevice();
        objectTemplateService.applyTemplate(BUS, DemoFixtureBootstrap.MQTT_METER_BUS_MODEL);
        objectManager.setVariableValue(
                BUS,
                "driverConfigJson",
                DataRecord.single(
                        DataSchema.builder("stringValue").field("value", FieldType.STRING).build(),
                        Map.of("value", DemoFixtureBootstrap.METER_INGRESS_DRIVER_CONFIG)
                )
        );

        assertThat(telemetryPolicyService.ingressPayloadLanes(BUS)).isTrue();

        for (int i = 1; i <= 50; i++) {
            String id = "meter-" + String.format("%04d", i);
            telemetryCoalescer.recordUpdate(
                    BUS,
                    "lastIngress",
                    DataRecord.single(
                            INGRESS_SCHEMA,
                            Map.of(
                                    "topic", "meter",
                                    "raw", "{\"id\":\"" + id + "\",\"temperature\":\"" + i + "\"}"
                            )
                    )
            );
        }

        telemetryCoalescer.flushNow();
        TimeUnit.MILLISECONDS.sleep(500);

        for (int i = 1; i <= 50; i++) {
            String path = "root.platform.instances.meter-" + String.format("%04d", i);
            assertThat(objectManager.tree().findByPath(path))
                    .as("instance %s should exist", path)
                    .isPresent();
            var temperature = objectManager.require(path).getVariable("temperature").orElseThrow().value().orElseThrow();
            assertThat(temperature.firstRow().get("value")).isEqualTo((double) i);
        }
    }

    private void ensureBusDevice() {
        if (objectManager.tree().findByPath(BUS).isEmpty()) {
            objectManager.create(
                    "root.platform.devices",
                    "mqtt-meter-bus-burst-test",
                    ObjectType.DEVICE,
                    "MQTT meter bus burst test",
                    "",
                    null
            );
        }
    }
}
