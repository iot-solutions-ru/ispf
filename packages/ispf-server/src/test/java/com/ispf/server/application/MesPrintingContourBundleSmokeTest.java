package com.ispf.server.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke for {@code examples/mes-printing-contour/bundle.json} (docanima printing contour).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MesPrintingContourBundleSmokeTest {

    private static final String HUB = "root.platform.devices.printing-contour-hub";
    private static final String WA_PLANNED = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String WA_ACTIVE = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deploysBundleAndRunsOperatorHappyPath() throws Exception {
        deployBundle();

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "mes_printing_generateOrder",
                                  "input": {
                                    "schema": {
                                      "name": "in",
                                      "fields": [
                                        { "name": "machineCode", "type": "STRING" },
                                        { "name": "projectName", "type": "STRING" },
                                        { "name": "productName", "type": "STRING" },
                                        { "name": "customerName", "type": "STRING" }
                                      ]
                                    },
                                    "rows": [{
                                      "machineCode": "PR120",
                                      "projectName": "Smoke batch",
                                      "productName": "Test sleeve",
                                      "customerName": "Smoke Customer"
                                    }]
                                  }
                                }
                                """.formatted(HUB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.orderNo").isNotEmpty())
                .andExpect(jsonPath("$.result.workAreaId").isNotEmpty());

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "mes_printing_listStages",
                                  "input": {
                                    "schema": {
                                      "name": "in",
                                      "fields": [{ "name": "machineCode", "type": "STRING" }]
                                    },
                                    "rows": [{ "machineCode": "PR120" }]
                                  }
                                }
                                """.formatted(HUB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows", hasSize(4)));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "mes_printing_pauseStage",
                                  "input": {
                                    "schema": {
                                      "name": "in",
                                      "fields": [{ "name": "workAreaId", "type": "STRING" }]
                                    },
                                    "rows": [{ "workAreaId": "%s" }]
                                  }
                                }
                                """.formatted(HUB, WA_ACTIVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "mes_printing_startStage",
                                  "input": {
                                    "schema": {
                                      "name": "in",
                                      "fields": [
                                        { "name": "workAreaId", "type": "STRING" },
                                        { "name": "startedBy", "type": "STRING" }
                                      ]
                                    },
                                    "rows": [{
                                      "workAreaId": "%s",
                                      "startedBy": "smoke-test"
                                    }]
                                  }
                                }
                                """.formatted(HUB, WA_PLANNED)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.status").value("IN_PROGRESS"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "mes_printing_registerInputRoll",
                                  "input": {
                                    "schema": {
                                      "name": "in",
                                      "fields": [
                                        { "name": "workAreaId", "type": "STRING" },
                                        { "name": "barcode", "type": "STRING" }
                                      ]
                                    },
                                    "rows": [{
                                      "workAreaId": "%s",
                                      "barcode": "IN-SMOKE-9001"
                                    }]
                                  }
                                }
                                """.formatted(HUB, WA_PLANNED)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "mes_printing_registerOutputRoll",
                                  "input": {
                                    "schema": {
                                      "name": "in",
                                      "fields": [
                                        { "name": "workAreaId", "type": "STRING" },
                                        { "name": "barcode", "type": "STRING" },
                                        { "name": "lengthM", "type": "INTEGER" }
                                      ]
                                    },
                                    "rows": [{
                                      "workAreaId": "%s",
                                      "barcode": "OUT-SMOKE-9001",
                                      "lengthM": 500
                                    }]
                                  }
                                }
                                """.formatted(HUB, WA_PLANNED)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "mes_printing_completeStage",
                                  "input": {
                                    "schema": {
                                      "name": "in",
                                      "fields": [{ "name": "workAreaId", "type": "STRING" }]
                                    },
                                    "rows": [{ "workAreaId": "%s" }]
                                  }
                                }
                                """.formatted(HUB, WA_PLANNED)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.status").value("COMPLETED"));

        mockMvc.perform(get("/api/v1/applications/mes-printing-contour/operator-ui"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appId").value("mes-printing-contour"))
                .andExpect(jsonPath("$.dashboards", hasSize(6)));
    }

    private void deployBundle() throws Exception {
        String bundle = new ClassPathResource("mes-printing-contour-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);
        mockMvc.perform(post("/api/v1/applications/mes-printing-contour/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));
    }
}
