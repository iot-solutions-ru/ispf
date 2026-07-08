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

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CI smoke for {@code examples/mes-platform-production/bundle.json} (BL-170 production walkthrough).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MesPlatformProductionBundleSmokeTest {

    private static final String HUB_DEVICE = "root.platform.devices.mes-platform-production-hub";
    private static final String SEED_SHIFT_ID = "dddddddd-dddd-dddd-dddd-dddddddddddd";
    private static final String SEED_BATCH_PATH = "root.platform.mes.lots.batch-line-a01-001";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deploysProductionBundleOeeQualityBatchAndErpOutbox() throws Exception {
        String bundle = new ClassPathResource("mes-platform-production-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/applications/mes-platform-production/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "mes_oee_getKpi",
                                  "input": {
                                    "schema": {
                                      "name": "in",
                                      "fields": [{ "name": "shiftId", "type": "STRING" }]
                                    },
                                    "rows": [{ "shiftId": "%s" }]
                                  }
                                }
                                """.formatted(HUB_DEVICE, SEED_SHIFT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.oeePct").value(greaterThan(80)));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "mes_quality_listSpcSamples",
                                  "input": {
                                    "schema": { "name": "in", "fields": [] },
                                    "rows": [{}]
                                  }
                                }
                                """.formatted(HUB_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows", hasSize(3)));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "mes_batch_runPhase",
                                  "input": {
                                    "schema": {
                                      "name": "in",
                                      "fields": [
                                        { "name": "batchPath", "type": "STRING" },
                                        { "name": "batchId", "type": "STRING" },
                                        { "name": "recipe", "type": "STRING" },
                                        { "name": "phase", "type": "STRING" }
                                      ]
                                    },
                                    "rows": [{
                                      "batchPath": "%s",
                                      "batchId": "BATCH-LINE-A01-001",
                                      "recipe": "recipe-standard-a",
                                      "phase": "react"
                                    }]
                                  }
                                }
                                """.formatted(HUB_DEVICE, SEED_BATCH_PATH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.phase").value("react"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "mes_erp_enqueueOutbox",
                                  "input": {
                                    "schema": {
                                      "name": "in",
                                      "fields": [
                                        { "name": "entityType", "type": "STRING" },
                                        { "name": "entityId", "type": "STRING" },
                                        { "name": "payloadJson", "type": "STRING" }
                                      ]
                                    },
                                    "rows": [{
                                      "entityType": "WORK_ORDER",
                                      "entityId": "WO-LINE-A01-001",
                                      "payloadJson": "{\\"status\\":\\"dispatched\\"}"
                                    }]
                                  }
                                }
                                """.formatted(HUB_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.idempotencyKey").value("WORK_ORDER:WO-LINE-A01-001"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "mes_erp_pollOutbox",
                                  "input": {
                                    "schema": { "name": "in", "fields": [] },
                                    "rows": [{}]
                                  }
                                }
                                """.formatted(HUB_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                // Schedule mes-erp-outbox-poll may drain pending rows before manual poll (BL-169).
                .andExpect(jsonPath("$.result.rows").isArray());
    }
}
