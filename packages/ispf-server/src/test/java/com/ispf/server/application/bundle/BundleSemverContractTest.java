package com.ispf.server.application.bundle;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BundleSemverContractTest {

    private static final String APP_ID = "warehouse-semver";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsInvalidSemverOnDeploy() throws Exception {
        String json = warehouseJson().replace("\"1.0.0\"", "\"v1.0.0\"");
        mockMvc.perform(post("/api/v1/applications/" + APP_ID + "/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validateRejectsInvalidSemver() throws Exception {
        String json = warehouseJson().replace("\"1.0.0\"", "\"1\"");
        mockMvc.perform(post("/api/v1/applications/" + APP_ID + "/bundle/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("semver")));
    }

    @Test
    void deploys100ThenUpgradeValidateAndDeploy110() throws Exception {
        String v100 = warehouseJson();
        mockMvc.perform(post("/api/v1/applications/" + APP_ID + "/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(v100))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.version").value("1.0.0"));

        String v110 = v100.replace("\"1.0.0\"", "\"1.1.0\"");
        mockMvc.perform(post("/api/v1/applications/" + APP_ID + "/bundle/validate?dryRun=true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(v110))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));

        mockMvc.perform(post("/api/v1/applications/" + APP_ID + "/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(v110))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("1.1.0"));
    }

    @Test
    void majorBumpProducesValidateWarning() throws Exception {
        String v100 = warehouseJson();
        mockMvc.perform(post("/api/v1/applications/" + APP_ID + "/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(v100))
                .andExpect(status().isOk());

        String v200 = v100.replace("\"1.0.0\"", "\"2.0.0\"");
        mockMvc.perform(post("/api/v1/applications/" + APP_ID + "/bundle/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(v200))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.warnings[0]").value(org.hamcrest.Matchers.containsString("Major version bump")));
    }

    private static String warehouseJson() throws Exception {
        return new ClassPathResource("warehouse-bundle.json").getContentAsString(StandardCharsets.UTF_8);
    }
}
