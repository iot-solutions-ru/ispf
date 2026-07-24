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
 * End-to-end certification of the {@code erp-mes-pharma} overlay bundle on top of
 * {@code erp-mes-core} (ISA-95 / ISA-88): core deploy -&gt; overlay deploy (requires
 * verification) -&gt; GMP event catalog -&gt; quarantine/batch release with e-signatures
 * (21 CFR Part 11 style) -&gt; double-verified dispensing with tolerance guard -&gt;
 * serialization registry -&gt; core functions on overlay data (job board, eBR sections).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Isolated
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ErpMesPharmaOverlayTest {

    private static final String CORE_HUB = "root.platform.singleton-blueprints.erp-mes-core-hub-v1";
    private static final String PHARMA_HUB = "root.platform.singleton-blueprints.erp-mes-pharma-hub-v1";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pharmaOverlayOnTopOfCore() throws Exception {
        deployBundle("erp-mes-core", "erp-mes-core-bundle.json");
        deployBundle("erp-mes-pharma", "erp-mes-pharma-bundle.json");

        // (a) GMP event catalog: 20 PHA-* codes, PHA-LC (line clearance) is a GMP-critical SETUP event
        invoke(PHARMA_HUB, "pha_eventdef_list", fields("section"), """
                {"section": ""}""")
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows.length()", greaterThan(19)))
                .andExpect(jsonPath("$.result.rows[?(@.code=='PHA-LC' && @.eventClass=='SETUP' && @.gmpCritical=='true')]",
                        hasSize(1)));

        // (b) batch release: LOT-FG-PH-0001 leaves QUARANTINE with an APPROVED e-signature;
        // a repeated release is refused
        invoke(PHARMA_HUB, "pha_lot_release", fields("lotId", "by", "comment"), """
                {"lotId": "LOT-FG-PH-0001", "by": "EMP-H03", "comment": "Batch record reviewed"}""")
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.disposition").value("RELEASED"));

        invoke(PHARMA_HUB, "pha_esign_list", fields("entityId"), """
                {"entityId": "LOT-FG-PH-0001"}""")
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows[?(@.meaning=='APPROVED' && @.signer=='EMP-H03')]",
                        hasSize(1)));

        invoke(PHARMA_HUB, "pha_lot_release", fields("lotId", "by", "comment"), """
                {"lotId": "LOT-FG-PH-0001", "by": "EMP-H03", "comment": ""}""")
                .andExpect(jsonPath("$.error_code").value("INVALID_STATE"));

        // seeded e-signature on the released API lot is present in the audit journal
        invoke(PHARMA_HUB, "pha_esign_list", fields("entityId"), """
                {"entityId": "LOT-API-0002"}""")
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows[?(@.meaning=='APPROVED')]", hasSize(1)));

        // (c) dispensing: double verification and tolerance guard
        invoke(PHARMA_HUB, "pha_dispense_weigh",
                fields("barcode", "targetQty", "actualQty", "tolerancePct", "weighedBy", "verifiedBy"),
                """
                {"barcode": "BC-EXC-0001", "targetQty": "100", "actualQty": "100.5",
                 "tolerancePct": "2", "weighedBy": "EMP-H02", "verifiedBy": "EMP-H01"}""")
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.lotId").value("LOT-EXC-0001"));

        invoke(PHARMA_HUB, "pha_esign_list", fields("entityId"), """
                {"entityId": "LOT-EXC-0001"}""")
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows[?(@.meaning=='AUTHORED' && @.signer=='EMP-H02')]",
                        hasSize(1)))
                .andExpect(jsonPath("$.result.rows[?(@.meaning=='REVIEWED' && @.signer=='EMP-H01')]",
                        hasSize(1)));

        // same person on both signatures is refused
        invoke(PHARMA_HUB, "pha_dispense_weigh",
                fields("barcode", "targetQty", "actualQty", "tolerancePct", "weighedBy", "verifiedBy"),
                """
                {"barcode": "BC-EXC-0001", "targetQty": "100", "actualQty": "100",
                 "tolerancePct": "2", "weighedBy": "EMP-H02", "verifiedBy": "EMP-H02"}""")
                .andExpect(jsonPath("$.error_code").value("SECOND_PERSON_REQUIRED"));

        // 10 % deviation exceeds the 2 % tolerance
        invoke(PHARMA_HUB, "pha_dispense_weigh",
                fields("barcode", "targetQty", "actualQty", "tolerancePct", "weighedBy", "verifiedBy"),
                """
                {"barcode": "BC-EXC-0001", "targetQty": "100", "actualQty": "110",
                 "tolerancePct": "2", "weighedBy": "EMP-H02", "verifiedBy": "EMP-H01"}""")
                .andExpect(jsonPath("$.error_code").value("TOLERANCE_EXCEEDED"));

        // (d) serialization: commission a DataMatrix code, duplicates are rejected
        invoke(PHARMA_HUB, "pha_serial_register",
                fields("serialCode", "jobNo", "lotId", "gtin", "parentCode"),
                """
                {"serialCode": "DM-PACK-0000000099", "jobNo": "JO-PH-003", "lotId": "LOT-FG-PH-0001",
                 "gtin": "04607012340018", "parentCode": "DM-BOX-0000000001"}""")
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.status").value("COMMISSIONED"));

        invoke(PHARMA_HUB, "pha_serial_register",
                fields("serialCode", "jobNo", "lotId", "gtin", "parentCode"),
                """
                {"serialCode": "DM-PACK-0000000099", "jobNo": "JO-PH-003", "lotId": "LOT-FG-PH-0001",
                 "gtin": "04607012340018", "parentCode": ""}""")
                .andExpect(jsonPath("$.error_code").value("DUPLICATE_SERIAL"));

        invoke(PHARMA_HUB, "pha_serial_list", fields("jobNo"), """
                {"jobNo": "JO-PH-003"}""")
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows[?(@.serialCode=='DM-BOX-0000000001')]", hasSize(1)));

        // (e) seeded board: JO-PH-001 RUNNING on the tablet press TPR-01
        invoke(CORE_HUB, "emc_joborder_listBoard", fields("equipmentId"), """
                {"equipmentId": "TPR-01"}""")
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows[?(@.jobNo=='JO-PH-001' && @.dispatchStatus=='RUNNING')]",
                        hasSize(1)));

        // (f) eBR: work record of JO-PH-001 carries the GMP sections
        invoke(CORE_HUB, "emc_wrec_get", fields("jobNo"), """
                {"jobNo": "JO-PH-001"}""")
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.recordId").value("WR-JO-PH-001"))
                .andExpect(jsonPath("$.result.sections[?(@.sectionKey=='lineClearance')]", hasSize(1)))
                .andExpect(jsonPath("$.result.sections[?(@.sectionKey=='ipcResults')]", hasSize(1)));

        // overlay hub live node carries the binding-fed counters
        mockMvc.perform(get("/api/v1/objects/by-path/variables/detail")
                        .param("path", PHARMA_HUB)
                        .param("name", "quarantineLotCount"))
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
