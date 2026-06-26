package com.ispf.server.application.script;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.server.bootstrap.FixtureModelBootstrap;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.ObjectTemplateService;
import com.ispf.server.object.RuntimeTelemetryCoalescer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PlatformScriptBridgeTest {

    private static final String INSTANCE_ID = "3123123123";
    private static final String INSTANCE_PATH = "root.platform.instances." + INSTANCE_ID;

    @Autowired
    private PlatformScriptBridge bridge;

    @Autowired
    private ObjectManager objectManager;

    @AfterEach
    void cleanup() {
        objectManager.tree().findByPath(INSTANCE_PATH).ifPresent(node -> objectManager.delete(INSTANCE_PATH));
    }

    @Test
    void jsonParseExtractsFields() {
        Map<String, Object> parsed = bridge.jsonParse(
                "{\"id\":\"3123123123\",\"temperature\":\"22\"}",
                List.of("id", "temperature")
        );
        assertThat(parsed.get("id")).isEqualTo("3123123123");
        assertThat(parsed.get("temperature")).isEqualTo("22");
    }

    @Test
    void instantiateModelIfMissingCreatesAndReusesMetersInstance() {
        String first = bridge.instantiateModelIfMissing(
                FixtureModelBootstrap.METERS_MODEL,
                "root.platform.instances",
                INSTANCE_ID
        );
        assertThat(first).isEqualTo(INSTANCE_PATH);
        assertThat(objectManager.tree().findByPath(INSTANCE_PATH)).isPresent();

        String second = bridge.instantiateModelIfMissing(
                FixtureModelBootstrap.METERS_MODEL,
                "root.platform.instances",
                INSTANCE_ID
        );
        assertThat(second).isEqualTo(INSTANCE_PATH);
    }

    @Test
    void setDriverTelemetryWritesTemperature() {
        bridge.instantiateModelIfMissing(
                FixtureModelBootstrap.METERS_MODEL,
                "root.platform.instances",
                INSTANCE_ID
        );
        bridge.setDriverTelemetry(
                INSTANCE_PATH,
                "temperature",
                Map.of("value", 22.0, "unit", "C")
        );
        var temperature = objectManager.require(INSTANCE_PATH).getVariable("temperature").orElseThrow().value().orElseThrow();
        assertThat(temperature.firstRow().get("value")).isEqualTo(22.0);
    }

    @Test
    void validateInstanceNameRejectsDots() {
        assertThatThrownBy(() -> PlatformScriptBridge.validateInstanceName("bad.id"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
