package com.ispf.server.function;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.server.bootstrap.DemoFixtureBootstrap;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.ObjectTemplateService;
import com.ispf.server.object.RuntimeTelemetryCoalescer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Isolated
@TestPropertySource(properties = {
        "ispf.object-change.async-enabled=true",
        "ispf.object-change.split-lanes-enabled=true",
        "ispf.object-change.coalesce-telemetry-updates=false",
        "ispf.runtime-telemetry.enabled=true",
        "ispf.runtime-telemetry.coalesce-ms=10"
})
class MeterMqttScriptDispatchIntegrationTest {

    private static final String BUS = "root.platform.devices.mqtt-meter-bus-script-test";
    private static final String INSTANCE_ID = "3123123123";
    private static final String INSTANCE_PATH = "root.platform.instances." + INSTANCE_ID;

    private static final DataSchema INGRESS_SCHEMA = DataSchema.builder("mqttIngress")
            .field("topic", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .build();

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private ObjectTemplateService objectTemplateService;

    @Autowired
    private FunctionService functionService;

    @Autowired
    private RuntimeTelemetryCoalescer telemetryCoalescer;

    @AfterEach
    void cleanup() {
        objectManager.tree().findByPath(INSTANCE_PATH).ifPresent(node -> objectManager.delete(INSTANCE_PATH));
        objectManager.tree().findByPath(BUS).ifPresent(node -> objectManager.delete(BUS));
    }

    @Test
    void ingestMeterPayloadCreatesInstanceAndUpdatesOnRepeat() throws Exception {
        ensureBusDevice();
        objectTemplateService.applyTemplate(BUS, DemoFixtureBootstrap.MQTT_METER_BUS_MODEL);

        DataRecord created = functionService.invoke(
                BUS,
                DemoFixtureBootstrap.INGEST_METER_PAYLOAD_FUNCTION,
                DataRecord.single(
                        INGRESS_SCHEMA,
                        Map.of("topic", "meter", "raw", "{\"id\":\"3123123123\",\"temperature\":\"22\"}")
                )
        );

        assertThat(created.firstRow().get("ok")).isEqualTo(true);
        assertThat(created.firstRow().get("routedPath")).isEqualTo(INSTANCE_PATH);
        telemetryCoalescer.flushNow();
        TimeUnit.MILLISECONDS.sleep(500);

        var temperature = objectManager.require(INSTANCE_PATH).getVariable("temperature").orElseThrow().value().orElseThrow();
        assertThat(temperature.firstRow().get("value")).isEqualTo(22.0);

        DataRecord updated = functionService.invoke(
                BUS,
                DemoFixtureBootstrap.INGEST_METER_PAYLOAD_FUNCTION,
                DataRecord.single(
                        INGRESS_SCHEMA,
                        Map.of("topic", "meter", "raw", "{\"id\":\"3123123123\",\"temperature\":\"25\"}")
                )
        );
        assertThat(updated.firstRow().get("ok")).isEqualTo(true);
        telemetryCoalescer.flushNow();
        TimeUnit.MILLISECONDS.sleep(500);

        temperature = objectManager.require(INSTANCE_PATH).getVariable("temperature").orElseThrow().value().orElseThrow();
        assertThat(temperature.firstRow().get("value")).isEqualTo(25.0);
    }

    private void ensureBusDevice() {
        if (objectManager.tree().findByPath(BUS).isEmpty()) {
            objectManager.create(
                    "root.platform.devices",
                    "mqtt-meter-bus-script-test",
                    ObjectType.DEVICE,
                    "MQTT meter bus script test",
                    "",
                    null
            );
        }
    }
}
