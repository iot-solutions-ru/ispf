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
 * Dedicated CI smoke for {@code examples/mes-oee-reference/bundle.json} (BL-121).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MesOeeReferenceBundleSmokeTest {

    private static final String HUB_DEVICE = "root.platform.devices.demo-sensor-01";
    private static final String SEED_SHIFT_ID = "dddddddd-dddd-dddd-dddd-dddddddddddd";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deploysMesOeeReferenceBundleListsShiftsAndReturnsKpiAbove80() throws Exception {
        String bundle = new ClassPathResource("mes-oee-reference-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/applications/mes-oee-reference/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));

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
                .andExpect(jsonPath("$.result.rows[0].shiftLabel").value("Morning"))
                .andExpect(jsonPath("$.result.rows[0].plannedMinutes").value(480))
                .andExpect(jsonPath("$.result.rows[0].downtimeMinutes").value(45));

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
    void addDowntimeIncreasesDowntimeMinutes() throws Exception {
        String bundle = new ClassPathResource("mes-oee-reference-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/applications/mes-oee-reference/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "mes_oee_addDowntime",
                                  "input": {
                                    "schema": {
                                      "name": "in",
                                      "fields": [
                                        { "name": "shiftId", "type": "STRING" },
                                        { "name": "minutes", "type": "INTEGER" }
                                      ]
                                    },
                                    "rows": [{ "shiftId": "%s", "minutes": 10 }]
                                  }
                                }
                                """.formatted(HUB_DEVICE, SEED_SHIFT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.downtimeMinutes").value(55));
    }
}
