package com.ispf.server.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BundleDependencyDeployApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deployFailsWhenRequiredBundleMissing() throws Exception {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("version", "1.0.0");
        manifest.put("displayName", "Dependent App");
        manifest.put("schemaName", "app_dependent_missing");
        manifest.put("requires", List.of(Map.of("appId", "missing-dep-never-deployed", "minVersion", "1.0.0")));
        manifest.put("objects", List.of(Map.of(
                "parentPath", "root.platform.devices",
                "name", "dep-probe",
                "type", "DEVICE"
        )));

        mockMvc.perform(post("/api/v1/applications/dependent-missing/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(manifest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.deployed").value(false))
                .andExpect(jsonPath("$.errors[0]", containsString("missing-dep-never-deployed")));
    }

    @Test
    void deploySucceedsWhenRequiredBundlePresent() throws Exception {
        // Other test classes share this Spring context and its database, and one of them deploys a
        // different "warehouse" 1.0.0. App ids here must not be deployed by any other test.
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String requiredAppId = "dep-required-" + suffix;
        Map<String, Object> required = new LinkedHashMap<>();
        required.put("version", "1.0.0");
        required.put("displayName", "Required App");
        required.put("schemaName", "app_dep_required_" + suffix);
        required.put("migrations", List.of(Map.of(
                "id", "required_schema",
                "sql", "CREATE TABLE IF NOT EXISTS dep_required (id INT PRIMARY KEY)"
        )));

        mockMvc.perform(post("/api/v1/applications/" + requiredAppId + "/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(required)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.status").value("OK"));

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("version", "1.0.0");
        manifest.put("displayName", "Dependent App");
        manifest.put("schemaName", "app_dependent_ok_" + suffix);
        manifest.put("requires", List.of(Map.of("appId", requiredAppId, "minVersion", "1.0.0")));
        manifest.put("objects", List.of(Map.of(
                "parentPath", "root.platform.devices",
                "name", "dep-probe-" + suffix,
                "type", "DEVICE"
        )));

        mockMvc.perform(post("/api/v1/applications/dependent-ok-" + suffix + "/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(manifest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));
    }

    private String asJson(Map<String, Object> manifest) throws Exception {
        return new tools.jackson.databind.ObjectMapper().writeValueAsString(manifest);
    }
}
