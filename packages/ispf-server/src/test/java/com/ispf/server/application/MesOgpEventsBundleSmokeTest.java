package com.ispf.server.application;

import com.ispf.server.application.bundle.BundleVisualGroupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
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

        mockMvc.perform(get("/api/v1/events").param("objectPath", HUB).param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].eventName", hasItem("ogpUnprocessedEvent")));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "ogp_registerEvent",
                                  "input": {
                                    "schema": {
                                      "name": "in",
                                      "fields": [
                                        { "name": "eventCode", "type": "STRING" },
                                        { "name": "deleteNow", "type": "STRING" },
                                        { "name": "rollId", "type": "STRING" },
                                        { "name": "startM", "type": "STRING" },
                                        { "name": "endM", "type": "STRING" },
                                        { "name": "startAt", "type": "STRING" },
                                        { "name": "endAt", "type": "STRING" },
                                        { "name": "wholeStage", "type": "STRING" },
                                        { "name": "side", "type": "STRING" },
                                        { "name": "rows", "type": "STRING" },
                                        { "name": "card1", "type": "STRING" },
                                        { "name": "card2", "type": "STRING" },
                                        { "name": "comment", "type": "STRING" },
                                        { "name": "subcodes", "type": "STRING" },
                                        { "name": "unprocessedId", "type": "STRING" }
                                      ]
                                    },
                                    "rows": [{
                                      "eventCode": "120",
                                      "deleteNow": "true",
                                      "rollId": "0",
                                      "startM": "0",
                                      "endM": "500",
                                      "startAt": "10:00:00",
                                      "endAt": "17:00:00",
                                      "wholeStage": "false",
                                      "side": "",
                                      "rows": "",
                                      "card1": "",
                                      "card2": "",
                                      "comment": "Необходимо взвесить в конце смены",
                                      "subcodes": "",
                                      "unprocessedId": ""
                                    }]
                                  }
                                }
                                """.formatted(HUB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "ogp_listProcessJournal",
                                  "input": {
                                    "schema": { "name": "in", "fields": [{ "name": "orderNo", "type": "STRING" }] },
                                    "rows": [{ "orderNo": "" }]
                                  }
                                }
                                """.formatted(HUB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows", hasSize(greaterThanOrEqualTo(1))));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "ogp_export1cBatch",
                                  "input": { "schema": { "name": "in", "fields": [] }, "rows": [{}] }
                                }
                                """.formatted(HUB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"));

        mockMvc.perform(get("/api/v1/applications/mes-ogp-events/operator-ui"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appId").value("mes-ogp-events"));
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
