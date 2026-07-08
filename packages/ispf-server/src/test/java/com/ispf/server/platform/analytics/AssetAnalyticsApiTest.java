package com.ispf.server.platform.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssetAnalyticsApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AssetAnalyticsService assetAnalyticsService;

    @Test
    void listsBuiltInRollingAvgAndRateOfChangeTemplates() throws Exception {
        assetAnalyticsService.ensureCatalog();

        mockMvc.perform(get("/api/v1/platform/analytics/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].templateId", hasItem("rollingAvg")))
                .andExpect(jsonPath("$[*].templateId", hasItem("rateOfChange")))
                .andExpect(jsonPath("$[*].templateId", hasItem("oee")))
                .andExpect(jsonPath("$[?(@.templateId=='rollingAvg')].helper").value(hasItem("rollingAvg")))
                .andExpect(jsonPath("$[?(@.templateId=='rateOfChange')].helper").value(hasItem("rateOfChange")));
    }
}
