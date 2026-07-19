package com.ispf.server.application.reference.mes;

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
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-193: genealogy lite; BL-221: tracked entity/activity DAG edges.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MesGenealogyLiteIntegrationTest {

    private static final String HUB = "root.platform.devices.mes-platform-hub";
    private static final String GENEALOGY_DASHBOARD = "root.platform.dashboards.mes-platform-genealogy";
    private static final String SEED_LOT = "BATCH-LINE-A01-001";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void genealogyBffAndDashboardWorkWithSeedData() throws Exception {
        deployBundle();

        mockMvc.perform(get("/api/v1/dashboards/by-path").param("path", GENEALOGY_DASHBOARD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("MES Genealogy"))
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='genealogy-by-lot')].functionName")
                        .value("mes_genealogy_queryByLot"))
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='genealogy-graph')].functionName")
                        .value("mes_genealogy_listGraph"))
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='genealogy-dag-by-lot')].functionName")
                        .value("mes_genealogy_queryDagByLot"))
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='genealogy-dag-edges')].functionName")
                        .value("mes_genealogy_listDagEdges"))
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='genealogy-report')].reportPath")
                        .value("root.platform.reports.mes-genealogy"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "mes_genealogy_listGraph",
                                  "input": {
                                    "schema": { "name": "in", "fields": [] },
                                    "rows": [{}]
                                  }
                                }
                                """.formatted(HUB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.result.rows[0].lotId").value(SEED_LOT))
                .andExpect(jsonPath("$.result.rows[0].materialCode").value("MAT-WIDGET-A01"))
                .andExpect(jsonPath("$.result.rows[0].workOrderNumber").value("WO-LINE-A01-001"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "mes_genealogy_queryByLot",
                                  "input": {
                                    "schema": {
                                      "name": "in",
                                      "fields": [{ "name": "lotId", "type": "STRING" }]
                                    },
                                    "rows": [{ "lotId": "%s" }]
                                  }
                                }
                                """.formatted(HUB, SEED_LOT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.lotId").value(SEED_LOT))
                .andExpect(jsonPath("$.result.rows", hasSize(2)))
                .andExpect(jsonPath("$.result.rows[0].qualityId").value("QR-LINE-A01-001"))
                .andExpect(jsonPath("$.result.rows[1].qualityId").value("QR-LINE-A01-002"))
                .andExpect(jsonPath("$.result.rows[1].defectCode").value("SCRATCH"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "mes_genealogy_queryDagByLot",
                                  "input": {
                                    "schema": {
                                      "name": "in",
                                      "fields": [{ "name": "lotId", "type": "STRING" }]
                                    },
                                    "rows": [{ "lotId": "%s" }]
                                  }
                                }
                                """.formatted(HUB, SEED_LOT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.lotId").value(SEED_LOT))
                .andExpect(jsonPath("$.result.rows", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.result.rows[0].fromEntityId").value("ENT-WIP-B01"))
                .andExpect(jsonPath("$.result.rows[0].toEntityId").value("ENT-FG-A01"))
                .andExpect(jsonPath("$.result.rows[0].activityId").value("ACT-TRANSFORM-2"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "mes_genealogy_listDagEdges",
                                  "input": {
                                    "schema": { "name": "in", "fields": [] },
                                    "rows": [{}]
                                  }
                                }
                                """.formatted(HUB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.result.rows[0].fromEntityId").value("ENT-RAW-A01"))
                .andExpect(jsonPath("$.result.rows[0].toEntityId").value("ENT-WIP-B01"))
                .andExpect(jsonPath("$.result.rows[1].fromEntityId").value("ENT-WIP-B01"))
                .andExpect(jsonPath("$.result.rows[1].toEntityId").value("ENT-FG-A01"));

        mockMvc.perform(post("/api/v1/applications/mes-platform/reports/mes-genealogy/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.rows[0].lot_id").value(SEED_LOT))
                .andExpect(jsonPath("$.rows[0].material_code").value("MAT-WIDGET-A01"))
                .andExpect(jsonPath("$.rows[0].work_order_number").value("WO-LINE-A01-001"));
    }

    private void deployBundle() throws Exception {
        String bundle = new ClassPathResource("mes-platform-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);
        mockMvc.perform(post("/api/v1/applications/mes-platform/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));
    }
}
