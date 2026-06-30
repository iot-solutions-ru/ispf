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

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationBundlePullFromTreeApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pullsDashboardsAndFunctionsFromLiveTree() throws Exception {
        String bundle = new ClassPathResource("lab-training-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/applications/lab-training/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/applications/lab-training/bundle/pull-from-tree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sections": ["dashboards", "functions"],
                                  "mergeActive": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appId").value("lab-training"))
                .andExpect(jsonPath("$.manifest.dashboards").isArray())
                .andExpect(jsonPath("$.pulled.dashboards").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.manifest.functions").isArray());
    }

    @Test
    void pullsSinglePathIntoManifest() throws Exception {
        String bundle = new ClassPathResource("lab-training-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/applications/lab-training/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/applications/lab-training/bundle/pull-from-tree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sections": ["dashboards"],
                                  "paths": ["root.platform.dashboards.lab-form-grid"],
                                  "mergeActive": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discoveredPaths[0]")
                        .value("root.platform.dashboards.lab-form-grid"))
                .andExpect(jsonPath("$.manifest.dashboards[0].path")
                        .value("root.platform.dashboards.lab-form-grid"));
    }
}
