package com.ispf.server.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.RuntimeTelemetryCoalescer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression for I-04: FULL telemetry + parse binding + constant MQTT bench payload must fire alerts.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ispf.object-change.async-enabled=true",
        "ispf.object-change.split-lanes-enabled=true",
        "ispf.object-change.demand-driven-publication=true",
        "ispf.object-change.coalesce-telemetry-updates=false",
        "ispf.runtime-telemetry.enabled=true",
        "ispf.runtime-telemetry.coalesce-enabled=false",
        "ispf.runtime-telemetry.ingress-queue-enabled=true",
        "ispf.runtime-telemetry.ingress-queue-coalesce-enabled=false",
        "ispf.runtime-telemetry.fast-historian-path=true",
        "ispf.event-journal.async-enabled=false"
})
class MqttSensorAutomationIngressTest {

    private static final String DEVICE = "root.platform.devices.mqtt-sensor-automation-test";
    private static final String RULE_NAME = "MQTT sensor automation ingress test";
    private static final DataSchema TEMPERATURE_RAW_SCHEMA = DataSchema.builder("temperature")
            .field("raw", FieldType.STRING)
            .build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private RuntimeTelemetryCoalescer telemetryCoalescer;

    @AfterEach
    void cleanup() throws Exception {
        deleteAlertRuleIfExists(RULE_NAME);
        objectManager.tree().findByPath(DEVICE).ifPresent(node -> objectManager.delete(DEVICE));
    }

    @Test
    void firesAlertOnRepeatedConstantRawPayload() throws Exception {
        ensureMqttSensorDevice();
        putParseBinding();
        createTemperatureAlertRule("self.temperature[\"value\"] > -1000.0");

        DataRecord constantPayload = DataRecord.single(TEMPERATURE_RAW_SCHEMA, Map.of("raw", "25.0"));
        for (int i = 0; i < 4; i++) {
            objectManager.setDriverTelemetryValue(DEVICE, "temperature", constantPayload);
        }
        telemetryCoalescer.flushNow();
        // Alert raises are journaled on the ALERT rule node (not the watched device).
        awaitEventOnPath(AutomationTreeService.rulePathForName(RULE_NAME), "thresholdExceeded", 10_000);
    }

    private void awaitEventOnPath(String objectPath, String eventName, long timeoutMs) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            String body = mockMvc.perform(get("/api/v1/events").param("objectPath", objectPath).param("limit", "20"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            if (body.contains(eventName)) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        throw new AssertionError(eventName + " event was not fired on " + objectPath + " within " + timeoutMs + "ms");
    }

    private void ensureMqttSensorDevice() throws Exception {
        if (objectManager.tree().findByPath(DEVICE).isPresent()) {
            return;
        }
        PlatformObject device = objectManager.create(
                "root.platform.devices",
                "mqtt-sensor-automation-test",
                ObjectType.DEVICE,
                "mqtt-sensor-automation-test",
                null,
                null
        );
        objectManager.persistNodeTree(device.path());
        upsertTemperatureVariable(device.path());
    }

    private void upsertTemperatureVariable(String devicePath) throws Exception {
        String body = """
                {
                  "name": "temperature",
                  "schema": {
                    "name": "temperature",
                    "fields": [
                      {"name": "value", "type": "DOUBLE"},
                      {"name": "unit", "type": "STRING"},
                      {"name": "raw", "type": "STRING"}
                    ]
                  },
                  "readable": true,
                  "writable": true,
                  "historyEnabled": true,
                  "initialValue": {
                    "schema": {
                      "name": "temperature",
                      "fields": [
                        {"name": "value", "type": "DOUBLE"},
                        {"name": "unit", "type": "STRING"},
                        {"name": "raw", "type": "STRING"}
                      ]
                    },
                    "rows": [{"value": 0.0, "unit": "C", "raw": ""}]
                  }
                }
                """;
        mockMvc.perform(post("/api/v1/objects/by-path/variables?path=" + devicePath)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/objects/by-path/events?path=" + devicePath)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "thresholdExceeded",
                                  "description": "Temperature exceeded configured threshold",
                                  "level": "WARNING",
                                  "schema": {
                                    "name": "temperature",
                                    "fields": [
                                      {"name": "value", "type": "DOUBLE"},
                                      {"name": "unit", "type": "STRING"}
                                    ]
                                  }
                                }
                                """))
                .andExpect(status().isOk());
    }

    private void putParseBinding() throws Exception {
        String bindings = """
                [{
                  "id": "parse-mqtt-temperature",
                  "name": "Parse MQTT temperature payload",
                  "enabled": true,
                  "order": 10,
                  "activators": {
                    "onStartup": true,
                    "onVariableChange": [{"objectPath": "self", "variableName": "temperature"}],
                    "periodicMs": 0
                  },
                  "condition": "",
                  "expression": "double(self.temperature.raw)",
                  "target": {"variableName": "temperature", "field": "value"}
                }]
                """;
        mockMvc.perform(put("/api/v1/objects/by-path/binding-rules?path=" + DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindings))
                .andExpect(status().isOk());
    }

    private void createTemperatureAlertRule(String conditionExpr) throws Exception {
        String body = new ObjectMapper().writeValueAsString(Map.of(
                "name", RULE_NAME,
                "objectPath", DEVICE,
                "watchVariable", "temperature",
                "conditionExpr", conditionExpr,
                "eventName", "thresholdExceeded",
                "payloadVariable", "temperature",
                "enabled", true,
                "edgeTrigger", false
        ));
        mockMvc.perform(post("/api/v1/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(AutomationTreeService.rulePathForName(RULE_NAME)));
    }

    private void deleteAlertRuleIfExists(String name) throws Exception {
        String body = mockMvc.perform(get("/api/v1/alert-rules"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String path = AutomationTreeService.rulePathForName(name);
        if (body.contains(path)) {
            var result = mockMvc.perform(delete("/api/v1/alert-rules/by-path").param("path", path))
                    .andReturn();
            assertThat(result.getResponse().getStatus()).isIn(200, 404);
        }
    }
}
