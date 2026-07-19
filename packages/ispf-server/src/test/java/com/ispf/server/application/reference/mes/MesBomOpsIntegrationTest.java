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

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-222: nested BoM and operation dependency graph on {@code mes-platform}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Isolated
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MesBomOpsIntegrationTest {

    private static final String HUB = "root.platform.devices.mes-platform-hub";
    private static final String BOM_OPS_DASHBOARD = "root.platform.dashboards.mes-platform-bom-ops";
    private static final String MATERIAL = "MAT-WIDGET-A01";
    private static final String WORK_ORDER = "WO-LINE-A01-001";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void bomExplosionAndOperationDependencyGraphUseSeedData() throws Exception {
        deployBundle();

        mockMvc.perform(get("/api/v1/dashboards/by-path").param("path", BOM_OPS_DASHBOARD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("MES BoM + Operations"))
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='bom-explode')].functionName")
                        .value("mes_bom_explode"))
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='ops-ready')].functionName")
                        .value("mes_ops_listReady"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bomExplodeBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.materialCode").value(MATERIAL))
                .andExpect(jsonPath("$.result.rows", hasSize(greaterThanOrEqualTo(2))));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(listReadyBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows[?(@.operationId=='OP-ASSEMBLE')]", hasSize(greaterThanOrEqualTo(1))));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completeBody("OP-ASSEMBLE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows[?(@.operationId=='OP-TEST')]", hasSize(greaterThanOrEqualTo(1))));
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

    private String bomExplodeBody() {
        return """
                {
                  "objectPath": "%s",
                  "functionName": "mes_bom_explode",
                  "input": {
                    "schema": {
                      "name": "in",
                      "fields": [{ "name": "materialCode", "type": "STRING" }]
                    },
                    "rows": [{ "materialCode": "%s" }]
                  }
                }
                """.formatted(HUB, MATERIAL);
    }

    private String listReadyBody() {
        return """
                {
                  "objectPath": "%s",
                  "functionName": "mes_ops_listReady",
                  "input": {
                    "schema": {
                      "name": "in",
                      "fields": [{ "name": "workOrderNumber", "type": "STRING" }]
                    },
                    "rows": [{ "workOrderNumber": "%s" }]
                  }
                }
                """.formatted(HUB, WORK_ORDER);
    }

    private String completeBody(String operationId) {
        return """
                {
                  "objectPath": "%s",
                  "functionName": "mes_ops_complete",
                  "input": {
                    "schema": {
                      "name": "in",
                      "fields": [
                        { "name": "workOrderNumber", "type": "STRING" },
                        { "name": "operationId", "type": "STRING" }
                      ]
                    },
                    "rows": [{
                      "workOrderNumber": "%s",
                      "operationId": "%s"
                    }]
                  }
                }
                """.formatted(HUB, WORK_ORDER, operationId);
    }
}
