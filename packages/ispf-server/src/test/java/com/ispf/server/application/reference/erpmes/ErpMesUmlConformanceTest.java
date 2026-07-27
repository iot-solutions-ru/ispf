package com.ispf.server.application.reference.erpmes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UML conformance for erp-mes-core 2.0: deploy bundle, smoke-invoke catalog BFF
 * functions from {@code uml-catalog.json} (copied next to test resources).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Isolated
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ErpMesUmlConformanceTest {

    private static final String HUB = "root.platform.singleton-blueprints.erp-mes-core-hub-v1";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void umlCatalogFunctionsSmoke() throws Exception {
        String bundle = new ClassPathResource("erp-mes-core-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);
        mockMvc.perform(post("/api/v1/applications/erp-mes-core/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));

        JsonNode catalog = MAPPER.readTree(
                new ClassPathResource("erp-mes-uml-catalog.json").getInputStream());
        assertThat(catalog.path("version").asText()).isEqualTo("2.2.0");
        assertThat(catalog.path("milestone").asText()).isEqualTo("M5");

        List<String> required = new ArrayList<>();
        for (JsonNode n : catalog.path("requiredFunctions")) {
            required.add(n.asText());
        }
        assertThat(required).isNotEmpty();

        // Smoke a representative subset that needs no special inputs
        invokeOk("emc_hierarchy_scope_list", "[]", "{}");
        invokeOk("emc_qualification_listSpecs", "[]", "{}");
        invokeOk("emc_assembledfrom_list",
                "[{\"name\":\"parentDefinitionId\",\"type\":\"STRING\"}]",
                "{\"parentDefinitionId\":\"FG-UNIT-PACKED\"}");
        invokeOk("emc_segment_param_list",
                "[{\"name\":\"segmentId\",\"type\":\"STRING\"}]",
                "{\"segmentId\":\"SEG-ASSEMBLE\"}");
        invokeOk("emc_product_segment_specs_list",
                "[{\"name\":\"productId\",\"type\":\"STRING\"}]",
                "{\"productId\":\"PD-UNIT-PACKED\"}");
        invokeOk("emc_opscap_children_list",
                "[{\"name\":\"capabilityId\",\"type\":\"STRING\"}]",
                "{\"capabilityId\":\"CAP-WU-A01-ASSEMBLE\"}");
        invokeOk("emc_workmaster_nodes_list",
                "[{\"name\":\"workMasterId\",\"type\":\"STRING\"}]",
                "{\"workMasterId\":\"WM-ROUTE-PACK\"}");
        invokeOk("emc_workdirective_list", "[]", "{}");
        invokeOk("emc_workperf_list", "[]", "{}");
        invokeOk("emc_sublot_list",
                "[{\"name\":\"lotId\",\"type\":\"STRING\"}]",
                "{\"lotId\":\"LOT-RAW-0001\"}");
        invokeOk("emc_joborder_param_list",
                "[{\"name\":\"jobNo\",\"type\":\"STRING\"}]",
                "{\"jobNo\":\"JO-DEMO-002\"}");
        invokeOk("emc_mom_listActivityBff", "[]", "{}");
        invokeOk("emc_classprop_list",
                "[{\"name\":\"ownerKind\",\"type\":\"STRING\"},{\"name\":\"ownerId\",\"type\":\"STRING\"}]",
                "{\"ownerKind\":\"EQUIPMENT_CLASS\",\"ownerId\":\"EQC-ASSEMBLY-MACHINE\"}");

        int part3 = 0;
        for (Iterator<JsonNode> it = catalog.path("part3Activities").elements(); it.hasNext(); ) {
            JsonNode row = it.next();
            assertThat(row.path("status").asText()).isEqualTo("covered");
            assertThat(row.path("functionName").asText()).isNotBlank();
            part3++;
        }
        assertThat(part3).isEqualTo(32);

        int coveredClasses = 0;
        for (JsonNode c : catalog.path("classes")) {
            assertThat(c.path("status").asText()).isNotEqualTo("missing");
            if ("covered".equals(c.path("status").asText())) {
                coveredClasses++;
            }
        }
        assertThat(coveredClasses).isGreaterThanOrEqualTo(30);
    }

    private void invokeOk(String functionName, String schemaFieldsJson, String rowJson) throws Exception {
        String body = """
                {
                  "objectPath": "%s",
                  "functionName": "%s",
                  "input": {
                    "schema": { "name": "in", "fields": %s },
                    "rows": [ %s ]
                  }
                }
                """.formatted(HUB, functionName, schemaFieldsJson, rowJson);
        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"));
    }
}
