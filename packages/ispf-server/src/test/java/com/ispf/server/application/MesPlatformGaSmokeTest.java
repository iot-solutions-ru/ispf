package com.ispf.server.application;

import com.ispf.server.schedule.ScheduleObjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Wave 8 GA smoke (BL-164…BL-170): single deploy of {@code mes-platform-production} verifies
 * OEE + dispatch + quality SPC + ISA-88 batch + ERP outbox + scheduled outbox worker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MesPlatformGaSmokeTest {

    private static final String HUB_DEVICE = "root.platform.devices.mes-platform-production-hub";
    private static final String SEED_SHIFT_ID = "dddddddd-dddd-dddd-dddd-dddddddddddd";
    private static final String SEED_BATCH_PATH = "root.platform.mes.lots.batch-line-a01-001";
    private static final String OUTBOX_SCHEDULE_ID = "mes-erp-outbox-poll";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScheduleObjectService scheduleObjectService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deploysFullMesPlatformAndVerifiesAllModules() throws Exception {
        String bundle = new ClassPathResource("mes-platform-production-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/applications/mes-platform-production/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));

        assertTrue(
                scheduleObjectService.listEnabled().stream()
                        .anyMatch(schedule -> OUTBOX_SCHEDULE_ID.equals(schedule.scheduleId())),
                "ERP outbox poll schedule must be enabled in production bundle (BL-169)");

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "mes_platform_listLines",
                                  "input": {
                                    "schema": { "name": "in", "fields": [] },
                                    "rows": [{}]
                                  }
                                }
                                """.formatted(HUB_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows", hasSize(1)))
                .andExpect(jsonPath("$.result.rows[0].lineCode").value("LINE-A01"));

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
                                  "functionName": "mes_dispatch_confirmWorkOrder",
                                  "input": {
                                    "schema": {
                                      "name": "in",
                                      "fields": [
                                        { "name": "workOrderPath", "type": "STRING" },
                                        { "name": "operatorId", "type": "STRING" }
                                      ]
                                    },
                                    "rows": [{
                                      "workOrderPath": "root.platform.mes.work-orders.wo-line-a01-001",
                                      "operatorId": "operator"
                                    }]
                                  }
                                }
                                """.formatted(HUB_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.confirmed").value(true));

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

        MvcResult pollResult = mockMvc.perform(post("/api/v1/bff/invoke")
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
                .andReturn();
        JsonNode pollRows = objectMapper.readTree(pollResult.getResponse().getContentAsString())
                .path("result")
                .path("rows");
        assertTrue(
                pollRows.size() == 0 || (pollRows.size() == 1 && "sent".equals(pollRows.get(0).path("status").asText())),
                "ERP outbox poll returns sent row or empty when schedule already drained queue (BL-169)");
    }
}
