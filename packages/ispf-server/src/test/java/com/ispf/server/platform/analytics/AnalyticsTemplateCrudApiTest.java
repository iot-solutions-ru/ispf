package com.ispf.server.platform.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class AnalyticsTemplateCrudApiTest {

    private static final String ROLLING_AVG_PATH = "root.platform.analytics.rollingAvg";
    private static final String CUSTOM_PATH = "root.platform.analytics.customAvgTest";
    private static final String DEMO_DEVICE = "root.platform.devices.demo-sensor-01";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AssetAnalyticsService assetAnalyticsService;

    @Test
    void getTemplateByPathReturnsBuiltInRollingAvg() throws Exception {
        assetAnalyticsService.ensureCatalog();

        mockMvc.perform(get("/api/v1/platform/analytics/templates/by-path")
                        .param("path", ROLLING_AVG_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateId").value("rollingAvg"))
                .andExpect(jsonPath("$.helper").value("rollingAvg"))
                .andExpect(jsonPath("$.blueprintName").value("rolling-avg-v1"));
    }

    @Test
    void updateTemplatePersistsSourceBinding() throws Exception {
        assetAnalyticsService.ensureCatalog();

        mockMvc.perform(put("/api/v1/platform/analytics/templates/by-path")
                        .param("path", ROLLING_AVG_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "path": "%s",
                                  "templateId": "rollingAvg",
                                  "displayName": "Rolling average",
                                  "description": "",
                                  "helper": "rollingAvg",
                                  "sourcePath": "%s",
                                  "sourceVariable": "temperature",
                                  "sourceField": "value",
                                  "windowBucket": "5m",
                                  "blueprintName": "rolling-avg-v1",
                                  "enabled": true
                                }
                                """.formatted(ROLLING_AVG_PATH, DEMO_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourcePath").value(DEMO_DEVICE))
                .andExpect(jsonPath("$.sourceVariable").value("temperature"));
    }

    @Test
    void createAndDeleteCustomTemplate() throws Exception {
        assetAnalyticsService.ensureCatalog();

        mockMvc.perform(post("/api/v1/platform/analytics/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateId": "customAvgTest",
                                  "displayName": "Custom avg test",
                                  "description": "Test template",
                                  "helper": "rollingAvg",
                                  "sourcePath": "",
                                  "sourceVariable": "",
                                  "sourceField": "value",
                                  "windowBucket": "15m",
                                  "blueprintName": "rolling-avg-v1",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value(CUSTOM_PATH))
                .andExpect(jsonPath("$.templateId").value("customAvgTest"));

        mockMvc.perform(delete("/api/v1/platform/analytics/templates/by-path")
                        .param("path", CUSTOM_PATH))
                .andExpect(status().isOk());
    }

    @Test
    void cannotDeleteBuiltInTemplate() throws Exception {
        assetAnalyticsService.ensureCatalog();

        mockMvc.perform(delete("/api/v1/platform/analytics/templates/by-path")
                        .param("path", ROLLING_AVG_PATH))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateTemplateRejectsInvalidWindowBucket() throws Exception {
        assetAnalyticsService.ensureCatalog();

        mockMvc.perform(put("/api/v1/platform/analytics/templates/by-path")
                        .param("path", ROLLING_AVG_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "path": "%s",
                                  "templateId": "rollingAvg",
                                  "displayName": "Rolling average",
                                  "description": "",
                                  "helper": "rollingAvg",
                                  "sourcePath": "",
                                  "sourceVariable": "",
                                  "sourceField": "value",
                                  "windowBucket": "2w",
                                  "blueprintName": "rolling-avg-v1",
                                  "enabled": true
                                }
                                """.formatted(ROLLING_AVG_PATH)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void applyRollingAvgTemplateToDevice() throws Exception {
        assetAnalyticsService.ensureCatalog();

        mockMvc.perform(post("/api/v1/platform/analytics/templates/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templatePath": "%s",
                                  "devicePath": "%s",
                                  "sourcePath": "%s",
                                  "sourceVariable": "temperature",
                                  "sourceField": "value",
                                  "windowBucket": "5m"
                                }
                                """.formatted(ROLLING_AVG_PATH, DEMO_DEVICE, DEMO_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devicePath").value(DEMO_DEVICE))
                .andExpect(jsonPath("$.templateId").value("rollingAvg"))
                .andExpect(jsonPath("$.refresh.devicePath").value(DEMO_DEVICE));

        mockMvc.perform(get("/api/v1/objects/by-path/variables")
                        .param("path", DEMO_DEVICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='derivedValue')]").exists());

        mockMvc.perform(post("/api/v1/platform/analytics/derived-tags/refresh")
                        .param("devicePath", DEMO_DEVICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devicePath").value(DEMO_DEVICE));

        mockMvc.perform(get("/api/v1/platform/analytics/derived-tags/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(org.hamcrest.Matchers.greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$", hasItem(DEMO_DEVICE)));
    }
}
