package com.ispf.server.application.reference.mes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-168 hardening: ISA-88 batch phase runner + Operator batch dashboard on {@code mes-platform}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Isolated
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MesBatchPhaseRunnerIntegrationTest {

    private static final String HUB = "root.platform.devices.mes-platform-hub";
    private static final String BATCH_PATH = "root.platform.mes.lots.batch-line-a01-001";
    private static final String BATCH_DASHBOARD = "root.platform.dashboards.mes-platform-batch";
    private static final String BATCH_ID = "BATCH-LINE-A01-001";
    private static final String RECIPE = "recipe-standard-a";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void batchPhaseRunnerAdvancesThroughChargeReactDischargeCycle() throws Exception {
        deployBundle();

        mockMvc.perform(get("/api/v1/objects/by-path").param("path", BATCH_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("LOT"));

        mockMvc.perform(get("/api/v1/dashboards/by-path").param("path", BATCH_DASHBOARD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("MES Batch (ISA-88)"))
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='batch-status')].functionName")
                        .value("mes_batch_getStatus"))
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='batch-run-react')].functionName")
                        .value("mes_batch_runPhase"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchStatusBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.phase").value("charge"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchRunPhaseBody("react")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.phase").value("react"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchStatusBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.phase").value("react"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchRunPhaseBody("discharge")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.phase").value("discharge"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchStatusBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.batchId").value(BATCH_ID))
                .andExpect(jsonPath("$.result.recipe").value(RECIPE))
                .andExpect(jsonPath("$.result.phase").value("discharge"));
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

    private String batchStatusBody() {
        return """
                {
                  "objectPath": "%s",
                  "functionName": "mes_batch_getStatus",
                  "input": {
                    "schema": {
                      "name": "in",
                      "fields": [{ "name": "batchPath", "type": "STRING" }]
                    },
                    "rows": [{ "batchPath": "%s" }]
                  }
                }
                """.formatted(HUB, BATCH_PATH);
    }

    private String batchRunPhaseBody(String phase) {
        return """
                {
                  "objectPath": "%s",
                  "functionName": "mes_batch_runPhase",
                  "input": {
                    "schema": {
                      "name": "in",
                      "fields": [
                        { "name": "batchPath", "type": "STRING" },
                        { "name": "batchId", "type": "STRING" },
                        { "name": "recipe", "type": "STRING" },
                        { "name": "phase", "type": "STRING" }
                      ]
                    },
                    "rows": [{
                      "batchPath": "%s",
                      "batchId": "%s",
                      "recipe": "%s",
                      "phase": "%s"
                    }]
                  }
                }
                """.formatted(HUB, BATCH_PATH, BATCH_ID, RECIPE, phase);
    }
}
