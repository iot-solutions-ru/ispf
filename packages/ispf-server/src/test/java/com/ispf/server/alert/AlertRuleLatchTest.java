package com.ispf.server.alert;

import com.ispf.core.object.EventLevel;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.ObjectTemplateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AlertRuleLatchTest {

    private static final String DEVICE = "root.platform.devices.alert-latch-test";
    private static final String RULE_NAME = "Alert latch integration test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private ObjectTemplateService objectTemplateService;

    @Autowired
    private AutomationTreeService automationTreeService;

    @AfterEach
    void cleanup() {
        String rulePath = AutomationTreeService.rulePathForName(RULE_NAME);
        if (objectManager.tree().findByPath(rulePath).isPresent()) {
            automationTreeService.deleteAlertRule(rulePath);
        }
        objectManager.tree().findByPath(DEVICE).ifPresent(node -> objectManager.delete(DEVICE));
    }

    @Test
    void firesRaiseThenClearWithDeactivateExpr() throws Exception {
        ensureDeviceWithEvents();
        createLatchRule();

        setTemperature(95.0);
        assertTrue(waitForEvent("thresholdExceeded", 5_000));

        AlertRule latched = automationTreeService.getAlertRule(AutomationTreeService.rulePathForName(RULE_NAME));
        assertTrue(Boolean.TRUE.equals(latched.latchedActive()));

        setTemperature(65.0);
        assertTrue(waitForEvent("thresholdCleared", 5_000));

        AlertRule cleared = automationTreeService.getAlertRule(AutomationTreeService.rulePathForName(RULE_NAME));
        assertTrue(!Boolean.TRUE.equals(cleared.latchedActive()));
    }

    private void ensureDeviceWithEvents() {
        if (objectManager.tree().findByPath(DEVICE).isEmpty()) {
            PlatformObject device = objectManager.create(
                    "root.platform.devices",
                    "alert-latch-test",
                    ObjectType.DEVICE,
                    "Alert latch test",
                    null,
                    "mqtt-sensor-v1"
            );
            objectTemplateService.applyTemplate(device.path(), "mqtt-sensor-v1");
            objectManager.persistNodeTree(device.path());
        }
        ensureEvent(DEVICE, "thresholdExceeded");
        ensureEvent(DEVICE, "thresholdCleared");
    }

    private void ensureEvent(String path, String eventName) {
        if (objectManager.require(path).events().containsKey(eventName)) {
            return;
        }
        objectManager.upsertEvent(path, new EventDescriptor(eventName, "Test event", null, EventLevel.WARNING));
    }

    private void createLatchRule() throws Exception {
        mockMvc.perform(post("/api/v1/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "objectPath": "%s",
                                  "watchVariable": "temperature",
                                  "conditionExpr": "self.temperature[\\"value\\"] > 80.0",
                                  "deactivateExpr": "self.temperature[\\"value\\"] < 70.0",
                                  "eventName": "thresholdExceeded",
                                  "clearEventName": "thresholdCleared",
                                  "payloadVariable": "temperature",
                                  "enabled": true,
                                  "edgeTrigger": true
                                }
                                """.formatted(RULE_NAME, DEVICE)))
                .andExpect(status().isOk());
    }

    private void setTemperature(double value) throws Exception {
        mockMvc.perform(put("/api/v1/objects/by-path/variables")
                        .param("path", DEVICE)
                        .param("name", "temperature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schema": {
                                    "name": "temperature",
                                    "fields": [
                                      {"name": "value", "type": "DOUBLE"},
                                      {"name": "unit", "type": "STRING"}
                                    ]
                                  },
                                  "rows": [{"value": %s, "unit": "C"}]
                                }
                                """.formatted(value)))
                .andExpect(status().isOk());
    }

    private boolean waitForEvent(String eventName, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            String body = mockMvc.perform(get("/api/v1/events").param("objectPath", DEVICE).param("limit", "20"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            if (body.contains("\"eventName\":\"" + eventName + "\"")) {
                return true;
            }
            Thread.sleep(200);
        }
        return false;
    }
}
