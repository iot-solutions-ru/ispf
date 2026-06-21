package com.ispf.server.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke test for {@code examples/lab-training/bundle.json}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "admin")
class LabTrainingBundleTest {

    private static final List<String> EXPECTED_PATHS = List.of(
            "root.platform.applications.lab-training",
            "root.platform.operator-apps.lab-training",
            "root.platform.data-sources.lab-training",
            "root.platform.devices.lab-userA-01",
            "root.platform.devices.lab-userB-01",
            "root.platform.reports.lab-all-devices-table",
            "root.platform.reports.lab-table-corrective",
            "root.platform.dashboards.lab-form-grid",
            "root.platform.dashboards.lab-calculator",
            "root.platform.dashboards.lab-event-gen",
            "root.platform.dashboards.lab-charts",
            "root.platform.dashboards.lab-pie",
            "root.platform.dashboards.lab-modal",
            "root.platform.dashboards.lab-history",
            "root.platform.dashboards.lab-fan-composite",
            "root.platform.dashboards.lab-virtual-overview",
            "root.platform.dashboards.lab-variable-editor",
            "root.platform.alert-rules.lab-sum-range-sustained-alert",
            "root.platform.alert-rules.lab-table-sum-threshold",
            "root.platform.correlators.lab-event1-latch-on",
            "root.platform.correlators.lab-event2-unlatch",
            "root.platform.correlators.lab-open-corrective-report"
    );

    @Autowired
    private MockMvc mockMvc;

    @Test
    void importsLabTrainingBundleAndVerifiesKeyPaths() throws Exception {
        String bundle = new ClassPathResource("lab-training-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/platform/packages/import")
                        .param("packageId", "lab-training")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.version").value("2.0.0"))
                .andExpect(jsonPath("$.applicationPath").value("root.platform.applications.lab-training"));

        for (String path : EXPECTED_PATHS) {
            mockMvc.perform(get("/api/v1/objects/by-path").param("path", path))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value(path));
        }

        mockMvc.perform(get("/api/v1/objects")
                        .param("parent", "root.platform.reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].path", hasItem("root.platform.reports.lab-all-devices-table")));

        mockMvc.perform(post("/api/v1/reports/by-path/run")
                        .param("path", "root.platform.reports.lab-all-devices-table")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportType").value("tree-variables"))
                .andExpect(jsonPath("$.columns").isArray());

        mockMvc.perform(get("/api/v1/applications/lab-training/operator-ui"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appId").value("lab-training"))
                .andExpect(jsonPath("$.defaultDashboard").value("root.platform.dashboards.lab-form-grid"));
    }
}
