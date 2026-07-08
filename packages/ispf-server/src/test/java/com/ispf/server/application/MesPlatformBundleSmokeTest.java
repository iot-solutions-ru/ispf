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
 * Dedicated CI smoke for {@code examples/mes-platform/bundle.json} (BL-164 / BL-165 / BL-166).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MesPlatformBundleSmokeTest {

    private static final String HUB_DEVICE = "root.platform.devices.mes-platform-hub";
    private static final String SEED_SHIFT_ID = "dddddddd-dddd-dddd-dddd-dddddddddddd";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deploysMesPlatformBundleListsLinesShiftsAndReturnsKpiAbove80() throws Exception {
        String bundle = new ClassPathResource("mes-platform-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/applications/mes-platform/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));

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
                                  "functionName": "mes_oee_listShifts",
                                  "input": {
                                    "schema": { "name": "in", "fields": [] },
                                    "rows": [{}]
                                  }
                                }
                                """.formatted(HUB_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows", hasSize(1)))
                .andExpect(jsonPath("$.result.rows[0].lineCode").value("LINE-A01"))
                .andExpect(jsonPath("$.result.rows[0].shiftLabel").value("Morning"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "mes_oee_getKpi",
                                  "input": {
                                    "schema": {
                                      "name": "in",
                                      "fields": [
                                        { "name": "shiftId", "type": "STRING" }
                                      ]
                                    },
                                    "rows": [{ "shiftId": "%s" }]
                                  }
                                }
                                """.formatted(HUB_DEVICE, SEED_SHIFT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.lineCode").value("LINE-A01"))
                .andExpect(jsonPath("$.result.oeePct").value(greaterThan(80)));
    }

    @Test
    void dispatchConfirmWorkOrderReturnsOk() throws Exception {
        String bundle = new ClassPathResource("mes-platform-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/applications/mes-platform/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle))
                .andExpect(status().isOk());

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
    }
}
