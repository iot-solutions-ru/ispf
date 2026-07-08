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
 * BL-160 hardening: OEE analytics template wired on {@code mes-platform-oee} dashboard.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MesOeeAnalyticsDashboardIntegrationTest {

    private static final String HUB = "root.platform.devices.mes-platform-hub";
    private static final String OEE_DASHBOARD = "root.platform.dashboards.mes-platform-oee";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void oeeDashboardHasAnalyticsTemplateChartAndOeePctVariable() throws Exception {
        deployBundle();

        mockMvc.perform(get("/api/v1/dashboards/by-path").param("path", OEE_DASHBOARD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("MES OEE"))
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
