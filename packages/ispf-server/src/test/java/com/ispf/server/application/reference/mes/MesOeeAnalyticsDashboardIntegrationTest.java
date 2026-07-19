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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-165 (+ BL-160): OEE Operator dashboard + BFF KPI on {@code mes-platform}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MesOeeAnalyticsDashboardIntegrationTest {

    private static final String HUB = "root.platform.devices.mes-platform-hub";
    private static final String OEE_DASHBOARD = "root.platform.dashboards.mes-platform-oee";
    private static final String SEED_SHIFT_ID = "dddddddd-dddd-dddd-dddd-dddddddddddd";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void oeeDashboardHasAnalyticsTemplateChartAndOeePctVariable() throws Exception {
        deployBundle();

        mockMvc.perform(get("/api/v1/dashboards/by-path").param("path", OEE_DASHBOARD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("MES OEE"))
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='oee-kpi')].functionName").value("mes_oee_getKpi"))
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='oee-shifts')].functionName").value("mes_oee_listShifts"))
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='oee-lines')].functionName").value("mes_platform_listLines"))
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='oee-analytics-chart')]").exists())
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='oee-analytics-chart')].type").value("chart"))
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='oee-analytics-chart')].variableName").value("oeePct"))
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='oee-analytics-chart')].analyticsTemplateId").value("oee"))
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='oee-analytics-chart')].objectPath").value(HUB))
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='oee-analytics-help')].type").value("html-snippet"));

        mockMvc.perform(get("/api/v1/objects/by-path/variables").param("path", HUB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='oeePct')]").exists())
                .andExpect(jsonPath("$[?(@.name=='oeePct')].historyEnabled").value(true));

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
                                """.formatted(HUB, SEED_SHIFT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.lineCode").value("LINE-A01"))
                .andExpect(jsonPath("$.result.oeePct").value(org.hamcrest.Matchers.greaterThan(80)));
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
