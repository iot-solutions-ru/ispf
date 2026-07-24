package com.ispf.server.application.reference.erpmes;

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
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end certification of the {@code erp-mes-printing} overlay bundle on top of
 * {@code erp-mes-core} (ISA-95 / IEC 62264): core deploy -&gt; overlay deploy (requires
 * verification) -&gt; Flexibase event catalog queries -&gt; roll stock with lot properties -&gt;
 * core functions consuming overlay data (event register, job board, job bag sections).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Isolated
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ErpMesPrintingOverlayTest {

    private static final String CORE_HUB = "root.platform.singleton-blueprints.erp-mes-core-hub-v1";
    private static final String PRINT_HUB = "root.platform.singleton-blueprints.erp-mes-printing-hub-v1";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void printingOverlayOnTopOfCore() throws Exception {
        deployBundle("erp-mes-core", "erp-mes-core-bundle.json");
        deployBundle("erp-mes-printing", "erp-mes-printing-bundle.json");

        // (a) Flexibase catalog: 155 overlay codes + 5 core definitions, OGP-120 is a SETUP event
        invoke(PRINT_HUB, "emp_eventdef_list", fields("section", "catalog"), """
                {"section": "", "catalog": ""}""")
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows.length()", greaterThan(150)))
                .andExpect(jsonPath("$.result.rows[?(@.code=='OGP-120' && @.eventClass=='SETUP')]",
                        hasSize(1)));

        // catalog filter: BM codes carry the Flexibase section attribute
        invoke(PRINT_HUB, "emp_eventdef_list", fields("section", "catalog"), """
                {"section": "", "catalog": "BM"}""")
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows[?(@.code=='BM-202' && @.section=='Packaging')]",
                        hasSize(1)));

        // Zero Pull setup tags dictionary
        invoke(PRINT_HUB, "emp_setuptag_list", fields(), "{}")
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows.length()", greaterThan(15)))
                .andExpect(jsonPath("$.result.rows[?(@.tag=='Zerropull')]", hasSize(1)));

        // (b) roll stock: FILM-PET12 roll carries width/thickness lot properties
        invoke(PRINT_HUB, "emp_roll_list", fields("definitionId"), """
                {"definitionId": "FILM-PET12"}""")
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows", hasSize(2)))
                .andExpect(jsonPath("$.result.rows[?(@.lotId=='LOT-FILM-0001' && @.widthMm=='1050' && @.thicknessMkm=='12')]",
                        hasSize(1)));

        // (c) cross-bundle: the core event register accepts an overlay catalog code
        invoke(CORE_HUB, "emc_event_register",
                fields("definitionCode", "jobNo", "equipmentId", "lotId", "lengthM", "timeMin", "comment", "by"),
                """
                {"definitionCode": "OGP-120", "jobNo": "JO-PRINT-001", "equipmentId": "PR120",
                 "lotId": "", "lengthM": "30", "timeMin": "45", "comment": "", "by": "operator"}""")
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.status").value("REGISTERED"));

        invoke(CORE_HUB, "emc_event_list", fields("equipmentId", "status"), """
                {"equipmentId": "PR120", "status": "OPEN"}""")
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows[?(@.definitionCode=='OGP-120' && @.oeeBucket=='AVAILABILITY')]",
                        hasSize(1)));

        // (d) seeded board: JO-PRINT-001 RUNNING on PR120; JO-PRINT-002 (ALLOWED, LM210) starts
        invoke(CORE_HUB, "emc_joborder_listBoard", fields("equipmentId"), """
                {"equipmentId": "PR120"}""")
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows[?(@.jobNo=='JO-PRINT-001' && @.dispatchStatus=='RUNNING')]",
                        hasSize(1)));

        invoke(CORE_HUB, "emc_joborder_start", fields("jobNo", "personId"), """
                {"jobNo": "JO-PRINT-002", "personId": "EMP-P01"}""")
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.status").value("RUNNING"));

        // (e) job bag: work record of JO-PRINT-001 has the printing sections
        invoke(CORE_HUB, "emc_wrec_get", fields("jobNo"), """
                {"jobNo": "JO-PRINT-001"}""")
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.recordId").value("WR-JO-PRINT-001"))
                .andExpect(jsonPath("$.result.sections[?(@.sectionKey=='colorControl')]", hasSize(1)))
                .andExpect(jsonPath("$.result.sections[?(@.sectionKey=='imposition')]", hasSize(1)));

        // overlay hub live node carries the binding-fed counters
        mockMvc.perform(get("/api/v1/objects/by-path/variables/detail")
                        .param("path", PRINT_HUB)
                        .param("name", "openPrintDowntimeCount"))
                .andExpect(status().isOk());
    }

    private void deployBundle(String appId, String resource) throws Exception {
        String bundle = new ClassPathResource(resource)
                .getContentAsString(StandardCharsets.UTF_8);
        mockMvc.perform(post("/api/v1/applications/" + appId + "/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));
    }

    private static String fields(String... names) {
        StringBuilder sb = new StringBuilder();
        for (String name : names) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append("{\"name\": \"").append(name).append("\", \"type\": \"STRING\"}");
        }
        return sb.toString();
    }

    private ResultActions invoke(String objectPath, String functionName, String schemaFields, String rowJson)
            throws Exception {
        String body = """
                {
                  "objectPath": "%s",
                  "functionName": "%s",
                  "input": {
                    "schema": { "name": "in", "fields": [ %s ] },
                    "rows": [ %s ]
                  }
                }
                """.formatted(objectPath, functionName, schemaFields, rowJson);
        return mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}
