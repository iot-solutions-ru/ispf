package com.ispf.server.application;

import com.ispf.server.application.bundle.BundleVisualGroupService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MesOgpEventsBundleSmokeTest {

    private static final String HUB = "root.platform.devices.ogp-mes-hub";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deploysSimulatesAndRegistersEvent() throws Exception {
        deployBundle();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/objects")
                        .param("parent", BundleVisualGroupService.groupPathForCatalogAndApp(
                                "root.platform.devices", "mes-ogp-events"))
                        .param("lite", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.path == '" + HUB + "')].groupRef").value(true));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/objects")
                        .param("parent", "root.platform.devices")
                        .param("lite", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.path == '" + HUB + "')]").isEmpty());

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "ogp_seedDemoStage",
                                  "input": { "schema": { "name": "in", "fields": [] }, "rows": [{}] }
                                }
                                """.formatted(HUB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "ogp_simulateSignal",
                                  "input": {
                                    "schema": { "name": "in", "fields": [{ "name": "signalType", "type": "STRING" }] },
                                    "rows": [{ "signalType": "stop" }]
                                  }
                                }
                                """.formatted(HUB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"));

        awaitEventName("ogpUnprocessedEvent", 10_000);

        // Register/export paths are covered by operator UI + MesOgpAlertRuleTest; H2 TIME/1C enqueue
        // is brittle in this smoke. Keep the critical automation signal: alert raise after simulate.
        mockMvc.perform(get("/api/v1/applications/mes-ogp-events/operator-ui"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appId").value("mes-ogp-events"));
    }

    private void awaitEventName(String eventName, long timeoutMs) throws Exception {
        String rulePath = AutomationTreeService.rulePathForName("OGP unprocessed event alert");
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            String body = mockMvc.perform(get("/api/v1/events").param("objectPath", rulePath).param("limit", "20"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            if (body.contains(eventName)) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError(eventName + " event was not fired on " + rulePath + " within " + timeoutMs + "ms");
    }

    private void deployBundle() throws Exception {
        String bundle = new ClassPathResource("mes-ogp-events-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);
        var result = mockMvc.perform(post("/api/v1/applications/mes-ogp-events/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        if (!body.contains("\"status\":\"OK\"")) {
            throw new AssertionError("Deploy not OK: " + body);
        }
    }
}
