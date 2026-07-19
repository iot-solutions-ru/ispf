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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dedicated CI smoke for {@code examples/mes-cto/bundle.json} (BL-223).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MesCtoBundleSmokeTest {

    private static final String HUB_DEVICE = "root.platform.devices.mes-cto-hub";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deploysMesCtoBundleValidatesOptionsAndGeneratesBuildDraft() throws Exception {
        deploy("mes-platform", "mes-platform-bundle.json");
        deploy("mes-cto", "mes-cto-bundle.json");

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "mes_cto_listOptions",
                                  "input": {
                                    "schema": { "name": "in", "fields": [] },
                                    "rows": [{}]
                                  }
                                }
                                """.formatted(HUB_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows", hasSize(4)))
                .andExpect(jsonPath("$.result.rows[0].familyCode").value("finish"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "mes_cto_validateConfig",
                                  "input": {
                                    "schema": {
                                      "name": "in",
                                      "fields": [
                                        { "name": "finishCode", "type": "STRING" },
                                        { "name": "sensorCode", "type": "STRING" }
                                      ]
                                    },
                                    "rows": [{
                                      "finishCode": "FINISH-MATTE",
                                      "sensorCode": "SENSOR-VISION"
                                    }]
                                  }
                                }
                                """.formatted(HUB_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error_message")
                        .value("Matte finish is not compatible with the vision sensor"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "mes_cto_generateBuild",
                                  "input": {
                                    "schema": {
                                      "name": "in",
                                      "fields": [
                                        { "name": "optionsJson", "type": "STRING" }
                                      ]
                                    },
                                    "rows": [{
                                      "optionsJson": "{\\"finishCode\\":\\"FINISH-GLOSS\\",\\"sensorCode\\":\\"SENSOR-VISION\\"}"
                                    }]
                                  }
                                }
                                """.formatted(HUB_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.materialCode").value("MAT-WIDGET-A01"))
                .andExpect(jsonPath("$.result.workOrderDraftId").value("WO-DRAFT-CTO-A01"))
                .andExpect(jsonPath("$.result.status").value("draft"));
    }

    private void deploy(String appId, String resourceName) throws Exception {
        String bundle = new ClassPathResource(resourceName).getContentAsString(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/applications/%s/deploy".formatted(appId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));
    }
}
