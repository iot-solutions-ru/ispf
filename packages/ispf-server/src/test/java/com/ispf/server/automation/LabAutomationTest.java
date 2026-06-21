package com.ispf.server.automation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lab Training automation v2: sustained alert delay and correlator SET_VARIABLE with payload filter.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LabAutomationTest {

    private static final String DEMO_DEVICE = "root.platform.devices.demo-sensor-01";

    @Autowired
    private MockMvc mockMvc;

    @AfterEach
    void cleanupLabArtifacts() throws Exception {
        deleteAlertRuleIfExists("Lab sustained delay rule");
        deleteCorrelatorIfExists("Lab payload filter set variable");
    }

    @Test
    void sustainedAlertFiresOnlyAfterDelayWhileConditionTrue() throws Exception {
        mockMvc.perform(post("/api/v1/drivers/runtime/stop").param("devicePath", DEMO_DEVICE))
                .andExpect(status().isOk());

        disableDemoAlertRule();

        String rulePath = AutomationTreeService.rulePathForName("Lab sustained delay rule");
        mockMvc.perform(post("/api/v1/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Lab sustained delay rule",
                                  "objectPath": "%s",
                                  "watchVariable": "temperature",
                                  "conditionExpr": "self.temperature[\\"value\\"] > 80.0",
                                  "eventName": "thresholdExceeded",
                                  "payloadVariable": "temperature",
                                  "enabled": true,
                                  "edgeTrigger": false,
                                  "delaySeconds": 2,
                                  "sustainWhileTrue": true
                                }
                                """.formatted(DEMO_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(rulePath));

        mockMvc.perform(put("/api/v1/objects/by-path/variables")
                        .param("path", DEMO_DEVICE)
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
                                  "rows": [{"value": 20.0, "unit": "C"}]
                                }
                                """))
                .andExpect(status().isOk());

        int baseline = countThresholdExceededEvents();

        mockMvc.perform(put("/api/v1/objects/by-path/variables")
                        .param("path", DEMO_DEVICE)
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
                                  "rows": [{"value": 95.0, "unit": "C"}]
                                }
                                """))
                .andExpect(status().isOk());

        assertEquals(baseline, countThresholdExceededEvents());

        Thread.sleep(2100L);

        mockMvc.perform(put("/api/v1/objects/by-path/variables")
                        .param("path", DEMO_DEVICE)
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
                                  "rows": [{"value": 96.0, "unit": "C"}]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/events").param("objectPath", DEMO_DEVICE).param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].eventName", hasItem("thresholdExceeded")));
        assertEquals(baseline + 1, countThresholdExceededEvents());
    }

    @Test
    void correlatorSetVariableRespectsPayloadFilter() throws Exception {
        disableDemoCorrelators();

        mockMvc.perform(put("/api/v1/objects/by-path/variables")
                        .param("path", DEMO_DEVICE)
                        .param("name", "alarmAcknowledged")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schema": {
                                    "name": "alarmAcknowledged",
                                    "fields": [{"name": "value", "type": "BOOLEAN"}]
                                  },
                                  "rows": [{"value": false}]
                                }
                                """))
                .andExpect(status().isOk());

        String correlatorPath = AutomationTreeService.correlatorPathForName("Lab payload filter set variable");
        mockMvc.perform(post("/api/v1/correlators")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Lab payload filter set variable",
                                  "objectPath": "%s",
                                  "patternType": "COUNT",
                                  "eventName": "thresholdExceeded",
                                  "windowSeconds": 0,
                                  "minOccurrences": 1,
                                  "cooldownSeconds": 0,
                                  "sequenceGapSeconds": 0,
                                  "actionType": "SET_VARIABLE",
                                  "actionTarget": "alarmAcknowledged=true",
                                  "payloadFilterExpr": "payload[\\"value\\"] > 90.0",
                                  "enabled": true
                                }
                                """.formatted(DEMO_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(correlatorPath));

        mockMvc.perform(post("/api/v1/events/fire")
                        .param("objectPath", DEMO_DEVICE)
                        .param("eventName", "thresholdExceeded")
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
                                  "rows": [{"value": 50.0, "unit": "C"}]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/objects/by-path/variables/detail")
                        .param("path", DEMO_DEVICE)
                        .param("name", "alarmAcknowledged"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.rows[0].value").value(false));

        mockMvc.perform(put("/api/v1/objects/by-path/variables")
                        .param("path", DEMO_DEVICE)
                        .param("name", "alarmAcknowledged")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schema": {
                                    "name": "alarmAcknowledged",
                                    "fields": [{"name": "value", "type": "BOOLEAN"}]
                                  },
                                  "rows": [{"value": false}]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/events/fire")
                        .param("objectPath", DEMO_DEVICE)
                        .param("eventName", "thresholdExceeded")
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
                                  "rows": [{"value": 95.0, "unit": "C"}]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/objects/by-path/variables/detail")
                        .param("path", DEMO_DEVICE)
                        .param("name", "alarmAcknowledged"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.rows[0].value").value(true));
    }

    private void disableDemoAlertRule() throws Exception {
        mockMvc.perform(put("/api/v1/objects/by-path/variables")
                        .param("path", "root.platform.alert-rules.temperature-threshold-exceeded")
                        .param("name", "enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schema": {
                                    "name": "enabled",
                                    "fields": [{"name": "value", "type": "BOOLEAN"}]
                                  },
                                  "rows": [{"value": false}]
                                }
                                """))
                .andExpect(status().isOk());
    }

    private void disableDemoCorrelators() throws Exception {
        for (String name : List.of(
                "Alarm handler on threshold event",
                "Recurring threshold escalation",
                "Threshold then alarm active (sequence demo)"
        )) {
            String path = AutomationTreeService.correlatorPathForName(name);
            mockMvc.perform(put("/api/v1/objects/by-path/variables")
                            .param("path", path)
                            .param("name", "enabled")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "schema": {
                                        "name": "enabled",
                                        "fields": [{"name": "value", "type": "BOOLEAN"}]
                                      },
                                      "rows": [{"value": false}]
                                    }
                                    """))
                    .andExpect(status().isOk());
        }
    }

    private int countThresholdExceededEvents() throws Exception {
        String body = mockMvc.perform(get("/api/v1/events").param("objectPath", DEMO_DEVICE).param("limit", "50"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        java.util.List<?> matches = com.jayway.jsonpath.JsonPath.read(
                body, "$[?(@.eventName == 'thresholdExceeded')]");
        return matches.size();
    }

    private void deleteAlertRuleIfExists(String name) throws Exception {
        String path = AutomationTreeService.rulePathForName(name);
        if (mockMvc.perform(get("/api/v1/alert-rules/by-path").param("path", path))
                .andReturn()
                .getResponse()
                .getStatus() == 200) {
            mockMvc.perform(delete("/api/v1/alert-rules/by-path").param("path", path))
                    .andExpect(status().isOk());
        }
    }

    private void deleteCorrelatorIfExists(String name) throws Exception {
        String path = AutomationTreeService.correlatorPathForName(name);
        if (mockMvc.perform(get("/api/v1/correlators/by-path").param("path", path))
                .andReturn()
                .getResponse()
                .getStatus() == 200) {
            mockMvc.perform(delete("/api/v1/correlators/by-path").param("path", path))
                    .andExpect(status().isOk());
        }
    }
}
