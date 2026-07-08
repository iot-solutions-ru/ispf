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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-167 hardening: SPC chart widget wired on {@code mes-platform-quality} dashboard.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MesQualitySpcDashboardIntegrationTest {

    private static final String HUB = "root.platform.devices.mes-platform-hub";
    private static final String QUALITY_DASHBOARD = "root.platform.dashboards.mes-platform-quality";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void qualityDashboardHasSpcChartAndControlLimitWidgets() throws Exception {
        deployBundle();

        mockMvc.perform(get("/api/v1/dashboards/by-path").param("path", QUALITY_DASHBOARD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("MES Quality / SPC"))
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='spc-chart')]").exists())
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='spc-chart')].type").value("chart"))
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='spc-chart')].variableName").value("spcMeasurement"))
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='spc-chart')].objectPath").value(HUB))
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='spc-ucl')].type").value("value"))
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='spc-lcl')].variableName").value("spcLcl"))
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='spc-samples')].functionName")
                        .value("mes_quality_listSpcSamples"));

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
                                """.formatted(HUB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows", hasSize(3)));
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
