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

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationBundleExportApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exportsActiveBundleAfterDeploy() throws Exception {
        String bundle = new ClassPathResource("warehouse-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/applications/warehouse/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/applications/warehouse/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appId").value("warehouse"))
                .andExpect(jsonPath("$.version").value(notNullValue()))
                .andExpect(jsonPath("$.manifest.version").value(notNullValue()))
                .andExpect(jsonPath("$.manifest.displayName").value(notNullValue()));
    }

    @Test
    void validatesBundleManifest() throws Exception {
        String bundle = new ClassPathResource("warehouse-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/applications/warehouse/bundle/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));
    }

    @Test
    void exportReturnsNotFoundWhenNoActiveBundle() throws Exception {
        mockMvc.perform(get("/api/v1/applications/no-such-app/export"))
                .andExpect(status().isNotFound());
    }
}
