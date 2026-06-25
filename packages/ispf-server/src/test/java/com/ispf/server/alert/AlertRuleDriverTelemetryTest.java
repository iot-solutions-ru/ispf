package com.ispf.server.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.ObjectTemplateService;
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
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ispf.object-change.async-enabled=true",
        "ispf.object-change.split-lanes-enabled=true",
        "ispf.object-change.coalesce-telemetry-updates=false",
        "ispf.runtime-telemetry.enabled=true",
        "ispf.runtime-telemetry.coalesce-ms=50",
        "ispf.event-journal.async-enabled=false"
})
class AlertRuleDriverTelemetryTest {

    private static final String DEVICE = "root.platform.devices.alert-driver-telemetry-test";
    private static final String RULE_NAME = "Alert driver telemetry test";
    private static final DataSchema WAVE_SCHEMA = DataSchema.builder("sineWave")
            .field("value", FieldType.DOUBLE)
            .build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private ObjectTemplateService objectTemplateService;

    @Autowired
    private RuntimeTelemetryCoalescer telemetryCoalescer;

    @AfterEach
    void cleanup() throws Exception {
        deleteAlertRuleIfExists(RULE_NAME);
        objectManager.tree().findByPath(DEVICE).ifPresent(node -> objectManager.delete(DEVICE));
    }

    @Test
    void firesEventWhenDriverTelemetryUpdatesWatchVariable() throws Exception {
        ensureVirtualLabDevice();
        createSineWaveAlertRule("self.sineWave[\"value\"] > -1000.0");

        objectManager.setDriverTelemetryValue(
                DEVICE,
                "sineWave",
                DataRecord.single(WAVE_SCHEMA, Map.of("value", 5.0))
        );
        telemetryCoalescer.flushNow();
        TimeUnit.MILLISECONDS.sleep(750);

        mockMvc.perform(get("/api/v1/events").param("objectPath", DEVICE).param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].eventName", hasItem("event1")));
    }

    private void ensureVirtualLabDevice() {
        if (objectManager.tree().findByPath(DEVICE).isPresent()) {
            return;
        }
        PlatformObject device = objectManager.create(
                "root.platform.devices",
                "alert-driver-telemetry-test",
                ObjectType.DEVICE,
                "Alert driver telemetry test",
                null,
                "virtual-lab-v1"
        );
        objectTemplateService.applyTemplate(device.path(), "virtual-lab-v1");
        objectManager.persistNodeTree(device.path());
    }

    private void createSineWaveAlertRule(String conditionExpr) throws Exception {
        String body = new ObjectMapper().writeValueAsString(Map.of(
                "name", RULE_NAME,
                "objectPath", DEVICE,
                "watchVariable", "sineWave",
                "conditionExpr", conditionExpr,
                "eventName", "event1",
                "payloadVariable", "sineWave",
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
            mockMvc.perform(delete("/api/v1/alert-rules/by-path").param("path", path))
                    .andExpect(status().isOk());
        }
    }
}
