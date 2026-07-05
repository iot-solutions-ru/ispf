package com.ispf.server.function;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.server.bootstrap.FixtureBlueprintBootstrap;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.ObjectTemplateService;
import com.ispf.server.object.RuntimeTelemetryCoalescer;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.server.plugin.blueprint.BlueprintApplicationService;
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
class MqttGatewayDispatchIntegrationTest {

    private static final String GATEWAY = "root.platform.devices.mqtt-gateway-dispatch-test";
    private static final String SENSORS = GATEWAY + ".sensors";
    private static final String SENSOR = SENSORS + ".loadtest-mqtt-sensor-00001";

    private static final DataSchema INGRESS_SCHEMA = DataSchema.builder("mqttIngress")
            .field("topic", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .build();

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private ObjectTemplateService objectTemplateService;

    @Autowired
    private BlueprintRegistry blueprintRegistry;

    @Autowired
    private BlueprintApplicationService blueprintApplicationService;

    @Autowired
    private FunctionService functionService;

    @Autowired
    private RuntimeTelemetryCoalescer telemetryCoalescer;

    @AfterEach
    void cleanup() {
        objectManager.tree().findByPath(SENSOR).ifPresent(node -> objectManager.delete(SENSOR));
        objectManager.tree().findByPath(SENSORS).ifPresent(node -> objectManager.delete(SENSORS));
        objectManager.tree().findByPath(GATEWAY).ifPresent(node -> objectManager.delete(GATEWAY));
    }

    @Test
    void dispatchTelemetryInstantiatesInstanceTypeAndRoutesPayload() throws Exception {
        ensureGatewayTree();
        objectTemplateService.applyTemplate(GATEWAY, MqttGatewayFunctionHandler.MODEL_NAME);

        objectManager.setVariableValue(
                GATEWAY,
                "sensorParentPath",
                DataRecord.single(
                        DataSchema.builder("stringValue").field("value", FieldType.STRING).build(),
                        Map.of("value", SENSORS)
                )
        );
        objectManager.setVariableValue(
                GATEWAY,
                "instanceModelName",
                DataRecord.single(
                        DataSchema.builder("stringValue").field("value", FieldType.STRING).build(),
                        Map.of("value", FixtureBlueprintBootstrap.MQTT_GATEWAY_SENSOR_MODEL)
                )
        );

        assertThat(objectManager.tree().findByPath(SENSOR)).isEmpty();

        DataRecord result = functionService.invoke(
                GATEWAY,
                MqttGatewayFunctionHandler.FUNCTION_NAME,
                DataRecord.single(
                        INGRESS_SCHEMA,
                        Map.of("topic", "ispf/loadtest/00001/temperature", "raw", "23.7")
                )
        );

        assertThat(result.firstRow().get("ok")).isEqualTo(true);
        assertThat(result.firstRow().get("routedPath")).isEqualTo(SENSOR);
        telemetryCoalescer.flushNow();
        TimeUnit.MILLISECONDS.sleep(100);

        var sensorNode = objectManager.require(SENSOR);
        assertThat(sensorNode.templateId()).contains(
                blueprintRegistry.requireByName(FixtureBlueprintBootstrap.MQTT_GATEWAY_SENSOR_MODEL).id()
        );
        var temperature = sensorNode.getVariable("temperature").orElseThrow().value().orElseThrow();
        assertThat(temperature.firstRow().get("value")).isEqualTo(23.7);
        assertThat(temperature.firstRow().get("unit")).isEqualTo("C");
    }

    @Test
    void preInstantiatedGatewaySensorAcceptsDispatch() throws Exception {
        ensureGatewayTree();
        objectTemplateService.applyTemplate(GATEWAY, MqttGatewayFunctionHandler.MODEL_NAME);
        objectManager.setVariableValue(
                GATEWAY,
                "sensorParentPath",
                DataRecord.single(
                        DataSchema.builder("stringValue").field("value", FieldType.STRING).build(),
                        Map.of("value", SENSORS)
                )
        );

        String modelId = blueprintRegistry.requireByName(FixtureBlueprintBootstrap.MQTT_GATEWAY_SENSOR_MODEL).id();
        blueprintApplicationService.instantiateWithRules(modelId, SENSORS, "loadtest-mqtt-sensor-00001", Map.of());

        DataRecord result = functionService.invoke(
                GATEWAY,
                MqttGatewayFunctionHandler.FUNCTION_NAME,
                DataRecord.single(
                        INGRESS_SCHEMA,
                        Map.of("topic", "ispf/loadtest/00001/temperature", "raw", "19.5")
                )
        );

        assertThat(result.firstRow().get("ok")).isEqualTo(true);
        telemetryCoalescer.flushNow();
        TimeUnit.MILLISECONDS.sleep(100);

        var temperature = objectManager.require(SENSOR).getVariable("temperature").orElseThrow().value().orElseThrow();
        assertThat(temperature.firstRow().get("value")).isEqualTo(19.5);
    }

    private void ensureGatewayTree() {
        if (objectManager.tree().findByPath(GATEWAY).isEmpty()) {
            objectManager.create(
                    "root.platform.devices",
                    "mqtt-gateway-dispatch-test",
                    ObjectType.DEVICE,
                    "MQTT gateway dispatch test",
                    "",
                    null
            );
        }
        if (objectManager.tree().findByPath(SENSORS).isEmpty()) {
            objectManager.create(GATEWAY, "sensors", ObjectType.CUSTOM, "Sensors", "", null);
        }
    }
}
