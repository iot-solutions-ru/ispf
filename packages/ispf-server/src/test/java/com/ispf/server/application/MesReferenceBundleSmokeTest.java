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
 * Dedicated CI smoke for {@code examples/mes-reference/bundle.json}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MesReferenceBundleSmokeTest {

    private static final String RACK_DEVICE = "root.platform.devices.demo-sensor-01";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deploysMesReferenceBundleAndListsDispatchOrders() throws Exception {
        String bundle = new ClassPathResource("mes-reference-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/applications/mes-reference/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "mes_listOrders",
                                  "input": {
                                    "schema": { "name": "in", "fields": [] },
                                    "rows": [{}]
                                  }
                                }
                                """.formatted(RACK_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows", hasSize(2)))
                .andExpect(jsonPath("$.result.rows[0].orderNo").value("DO-1001"))
                .andExpect(jsonPath("$.result.rows[0].status").value("pending"));
    }

    @Test
    void startAndCompleteFillingLifecycle() throws Exception {
        String bundle = new ClassPathResource("mes-reference-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/applications/mes-reference/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "mes_startFilling",
                                  "input": {
                                    "schema": {
                                      "name": "in",
                                      "fields": [
                                        { "name": "orderNo", "type": "STRING" }
                                      ]
                                    },
                                    "rows": [{ "orderNo": "DO-1001" }]
                                  }
                                }
                                """.formatted(RACK_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.status").value("filling"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "mes_completeFilling",
                                  "input": {
                                    "schema": {
                                      "name": "in",
                                      "fields": [
                                        { "name": "orderNo", "type": "STRING" }
                                      ]
                                    },
                                    "rows": [{ "orderNo": "DO-1001" }]
                                  }
                                }
                                """.formatted(RACK_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.status").value("completed"));
    }
}
