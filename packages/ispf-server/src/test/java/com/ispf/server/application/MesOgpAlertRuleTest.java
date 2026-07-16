package com.ispf.server.application;

import com.ispf.server.automation.AutomationTreeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MesOgpAlertRuleTest {

    private static final String HUB = "root.platform.devices.ogp-mes-hub";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AutomationTreeService automationTreeService;

    @Test
    void alertRuleFiresAfterUnprocessedPendingRefresh() throws Exception {
        deployBundle();

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "ogp_seedDemoStage",
                                  "input": { "schema": { "name": "in", "fields": [] }, "rows": [{}] }
                                }
                                """.formatted(HUB)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "ogp_simulateSignal",
                                  "input": {
                                    "schema": { "name": "in", "fields": [{ "name": "signalType", "type": "STRING" }] },
                                    "rows": [{ "signalType": "knife" }]
                                  }
                                }
                                """.formatted(HUB)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/objects/by-path/variables")
                        .param("path", HUB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='unprocessedPending')].value.rows[0].value").value(1.0));

        var rules = automationTreeService.findEnabledAlertRules(HUB, "unprocessedPending");
        assertFalse(rules.isEmpty(), "Expected OGP alert rule in index");

        mockMvc.perform(get("/api/v1/events")
                        .param("objectPath", AutomationTreeService.rulePathForName("OGP unprocessed event alert"))
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.eventName=='ogpUnprocessedEvent')]").exists());
    }

    private void deployBundle() throws Exception {
        String bundle = new ClassPathResource("mes-ogp-events-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);
        mockMvc.perform(post("/api/v1/applications/mes-ogp-events/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle))
                .andExpect(status().isOk());
    }
}
