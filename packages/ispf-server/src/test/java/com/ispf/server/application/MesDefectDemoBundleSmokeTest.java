package com.ispf.server.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MesDefectDemoBundleSmokeTest {

    private static final String HUB = "root.platform.devices.mes-hub-01";
    private static final String WORKFLOW = "root.platform.workflows.mes-defect-distribution";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deploysBundleListsLinesAndRoutesDefect() throws Exception {
        deployBundle();

        mockMvc.perform(get("/api/v1/workflows/by-path").param("path", WORKFLOW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.operatorAppId").value("mes-defect-demo"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "mes_listLines",
                                  "input": { "schema": { "name": "in", "fields": [] }, "rows": [{}] }
                                }
                                """.formatted(HUB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows", hasSize(3)));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "mes_simulateDefect",
                                  "input": {
                                    "schema": {
                                      "name": "in",
                                      "fields": [
                                        { "name": "lineCode", "type": "STRING" },
                                        { "name": "volumeKg", "type": "STRING" },
                                        { "name": "isSpecialScrap", "type": "STRING" },
                                        { "name": "orderScenario", "type": "STRING" }
                                      ]
                                    },
                                    "rows": [{
                                      "lineCode": "LINE-A01",
                                      "volumeKg": "12",
                                      "isSpecialScrap": "0",
                                      "orderScenario": "active"
                                    }]
                                  }
                                }
                                """.formatted(HUB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"));

        mockMvc.perform(post("/api/v1/workflows/by-path/run")
                        .param("path", WORKFLOW)
                        .param("triggerObjectPath", HUB))
                .andExpect(status().isOk());

        String taskId = awaitWorkQueueTask();
        mockMvc.perform(post("/api/v1/work-queue/claim")
                        .param("taskId", taskId)
                        .param("operatorId", "operator"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/work-queue/complete")
                        .param("taskId", taskId)
                        .param("operatorId", "operator"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/workflows/by-path").param("path", WORKFLOW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceState").value(org.hamcrest.Matchers.containsString("COMPLETED")));
    }

    @Test
    void specialScrapRoutesToAlternatePath() throws Exception {
        deployBundle();

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "mes_simulateDefect",
                                  "input": {
                                    "schema": {
                                      "name": "in",
                                      "fields": [
                                        { "name": "lineCode", "type": "STRING" },
                                        { "name": "volumeKg", "type": "STRING" },
                                        { "name": "isSpecialScrap", "type": "STRING" },
                                        { "name": "orderScenario", "type": "STRING" }
                                      ]
                                    },
                                    "rows": [{
                                      "lineCode": "LINE-D01",
                                      "volumeKg": "5",
                                      "isSpecialScrap": "1",
                                      "orderScenario": "active"
                                    }]
                                  }
                                }
                                """.formatted(HUB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"));

        mockMvc.perform(post("/api/v1/workflows/by-path/run")
                        .param("path", WORKFLOW)
                        .param("triggerObjectPath", HUB))
                .andExpect(status().isOk());

        String taskId = awaitWorkQueueTask();
        mockMvc.perform(post("/api/v1/work-queue/claim").param("taskId", taskId).param("operatorId", "operator"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/work-queue/complete").param("taskId", taskId).param("operatorId", "operator"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/applications/mes-defect-demo/reports/mes-defect-pending-recommendations/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parameters\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].target").value("SPECIAL_ROUTE"));
    }

    private void deployBundle() throws Exception {
        String bundle = new ClassPathResource("mes-defect-demo-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);
        mockMvc.perform(post("/api/v1/applications/mes-defect-demo/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));
    }

    private String awaitWorkQueueTask() throws Exception {
        for (int i = 0; i < 40; i++) {
            MvcResult result = mockMvc.perform(get("/api/v1/work-queue").param("operatorAppId", "mes-defect-demo"))
                    .andExpect(status().isOk())
                    .andReturn();
            String json = result.getResponse().getContentAsString();
            if (json.contains("\"taskId\"")) {
                int idx = json.indexOf("\"taskId\":\"");
                int start = idx + "\"taskId\":\"".length();
                int end = json.indexOf('"', start);
                return json.substring(start, end);
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("Work queue task did not appear");
    }
}
