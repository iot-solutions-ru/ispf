package com.ispf.server.ml;

import com.ispf.server.alert.AlertRuleService;
import com.ispf.server.automation.AutomationTreeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
@TestPropertySource(properties = {
        "ispf.ml.anomaly.enabled=true",
        "ispf.ml.anomaly.threshold-min=0",
        "ispf.ml.anomaly.threshold-max=100",
        "ispf.ml.anomaly.default-threshold=0.5"
})
class AnomalyAlertRuleIntegrationTest {

    private static final String DEMO_DEVICE = "root.platform.devices.demo-sensor-01";
    private static final String RULE_NAME = "Anomaly threshold integration test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AutomationTreeService automationTreeService;

    @Autowired
    private AlertRuleService alertRuleService;

    @AfterEach
    void cleanup() throws Exception {
        String path = AutomationTreeService.rulePathForName(RULE_NAME);
        if (automationTreeService.listAlertRules().stream().anyMatch(rule -> RULE_NAME.equals(rule.name()))) {
            alertRuleService.delete(path);
        }
    }

    @Test
    void firesPlatformEventWhenAnomalyThresholdBreached() throws Exception {
        postAlertRule()
                .andExpect(status().isOk());

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
                                  "rows": [{"value": 150.0, "unit": "C"}]
                                }
                                """))
                .andExpect(status().isOk());

        assertTrue(
                waitForEvent("thresholdExceeded"),
                "thresholdExceeded event was not fired by anomaly alert rule"
        );
    }

    private org.springframework.test.web.servlet.ResultActions postAlertRule() throws Exception {
        return mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "objectPath": "%s",
                                  "watchVariable": "temperature",
                                  "conditionExpr": "",
                                  "eventName": "thresholdExceeded",
                                  "payloadVariable": "temperature",
                                  "enabled": true,
                                  "edgeTrigger": true,
                                  "anomalyModelId": "threshold-v1"
                                }
                                """.formatted(RULE_NAME, DEMO_DEVICE))
        );
    }

    private boolean waitForEvent(String eventName) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            String body = mockMvc.perform(get("/api/v1/events/history")
                            .param("objectPath", DEMO_DEVICE)
                            .param("limit", "20"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            if (body.contains("\"eventName\":\"%s\"".formatted(eventName))) {
                return true;
            }
            Thread.sleep(200);
        }
        return false;
    }
}
