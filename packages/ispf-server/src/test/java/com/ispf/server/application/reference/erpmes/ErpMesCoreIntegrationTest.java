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

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end certification of the {@code erp-mes-core} bundle (ISA-95 / IEC 62264):
 * schedule receive -&gt; release -&gt; start (+resource conflict) -&gt; consume/produce -&gt;
 * genealogy -&gt; QA gate on complete -&gt; ERP outbox idempotency/ACK -&gt; OEE shift KPI.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Isolated
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ErpMesCoreIntegrationTest {

    private static final String HUB = "root.platform.singleton-blueprints.erp-mes-core-hub-v1";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void isa95HappyPathFromErpScheduleToOee() throws Exception {
        deployBundle();

        // Part 5 (PROCESS OperationsSchedule) -> Part 4 Work Schedule/Request/Job Order
        invoke("emc_schedule_receive",
                fields("externalRef", "requestId", "jobNo", "workMasterId", "workMasterVersion",
                        "equipmentId", "productDefinitionId", "quantity", "uom", "priority",
                        "plannedStart", "plannedEnd"),
                """
                {"externalRef": "ERP-PO-IT-1", "requestId": "WR-IT-001", "jobNo": "JO-IT-001",
                 "workMasterId": "WM-PACK", "workMasterVersion": "1", "equipmentId": "WU-A02",
                 "productDefinitionId": "FG-UNIT-PACKED", "quantity": "50", "uom": "pcs", "priority": "3",
                 "plannedStart": "", "plannedEnd": ""}""")
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.dispatchStatus").value("NOT_ALLOWED"));

        invoke("emc_joborder_release", fields("jobNo"), """
                {"jobNo": "JO-IT-001"}""")
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.dispatchStatus").value("ALLOWED"));

        // seeded JO-DEMO-001 is ALLOWED on WU-A02 -> starts; JO-DEMO-003 conflicts on the same unit
        invoke("emc_joborder_start", fields("jobNo", "personId"), """
                {"jobNo": "JO-DEMO-001", "personId": "EMP-003"}""")
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.status").value("RUNNING"));

        invoke("emc_joborder_start", fields("jobNo", "personId"), """
                {"jobNo": "JO-DEMO-003", "personId": "EMP-003"}""")
                .andExpect(jsonPath("$.error_code").value("RESOURCE_CONFLICT"));

        // material flow on the seeded RUNNING job JO-DEMO-002 (assembly, WU-A01)
        invoke("emc_matlot_placeOnLine", fields("barcode", "jobNo"), """
                {"barcode": "BC-RAW-0001", "jobNo": "JO-DEMO-002"}""")
                .andExpect(jsonPath("$.error_code").value("OK"));

        invoke("emc_matlot_consume", fields("barcode", "quantity"), """
                {"barcode": "BC-RAW-0001", "quantity": "10"}""")
                .andExpect(jsonPath("$.error_code").value("OK"));

        invoke("emc_matlot_produce",
                fields("jobNo", "lotId", "barcode", "definitionId", "quantity", "storageLocation"),
                """
                {"jobNo": "JO-DEMO-002", "lotId": "LOT-WIP-9999", "barcode": "BC-WIP-9999",
                 "definitionId": "WIP-HOUSING", "quantity": "10", "storageLocation": "WH-CENTRAL"}""")
                .andExpect(jsonPath("$.error_code").value("OK"));

        invoke("emc_track_genealogyByLot", fields("lotId"), """
                {"lotId": "LOT-WIP-9999"}""")
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows[?(@.direction=='INPUT' && @.lotId=='LOT-RAW-0001')]",
                        hasSize(greaterThanOrEqualTo(1))));

        // QA gate: confirmed defect blocks completion until closed
        invoke("emc_qa_registerDefect",
                fields("defectNo", "jobNo", "defectTypeId", "reasonCode", "lotId",
                        "qtyDeclared", "severity", "createdBy"),
                """
                {"defectNo": "DF-IT-1", "jobNo": "JO-DEMO-002", "defectTypeId": "DFT-VISUAL",
                 "reasonCode": "RC-MATERIAL", "lotId": "LOT-WIP-9999", "qtyDeclared": "2",
                 "severity": "MINOR", "createdBy": "operator"}""")
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.status").value("REGISTERED"));

        invoke("emc_qa_confirmDefect", fields("defectNo", "by", "reasonCode", "qtyConfirmed"), """
                {"defectNo": "DF-IT-1", "by": "qa", "reasonCode": "", "qtyConfirmed": ""}""")
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.status").value("CONFIRMED"));

        invoke("emc_joborder_complete", fields("jobNo"), """
                {"jobNo": "JO-DEMO-002"}""")
                .andExpect(jsonPath("$.error_code").value("QC_GATE_BLOCKED"));

        invoke("emc_qa_closeDefect", fields("defectNo", "by"), """
                {"defectNo": "DF-IT-1", "by": "qa"}""")
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.status").value("CLOSED"));

        invoke("emc_joborder_complete", fields("jobNo"), """
                {"jobNo": "JO-DEMO-002"}""")
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.status").value("ENDED"));

        // Part 5 outbox: idempotent enqueue + simulated transport ACK
        for (int i = 0; i < 2; i++) {
            invoke("emc_erp_enqueueOutbox",
                    fields("verb", "noun", "objectId", "payloadJson", "idempotencyKey"),
                    """
                    {"verb": "PROCESS", "noun": "MATERIAL_LOT", "objectId": "LOT-WIP-9999",
                     "payloadJson": "{\\"lotId\\":\\"LOT-WIP-9999\\"}", "idempotencyKey": "IT-KEY-1"}""")
                    .andExpect(jsonPath("$.error_code").value("OK"));
        }

        invoke("emc_erp_listOutbox", fields(), "{}")
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows[?(@.idempotencyKey=='IT-KEY-1')]", hasSize(1)));

        invoke("emc_erp_pollOutbox", fields("simulate"), """
                {"simulate": "true"}""")
                .andExpect(jsonPath("$.error_code").value("OK"));

        invoke("emc_erp_listOutbox", fields(), "{}")
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows[?(@.status=='ACKED')]",
                        hasSize(greaterThanOrEqualTo(1))));

        // OEE (ISO 22400 subset): produced qty above feeds the shift KPI of WU-A01
        invoke("emc_oee_calcShift", fields("equipmentId", "shiftLabel", "plannedMinutes"), """
                {"equipmentId": "WU-A01", "shiftLabel": "IT-SHIFT", "plannedMinutes": "480"}""")
                .andExpect(jsonPath("$.error_code").value("OK"));

        invoke("emc_oee_getKpi", fields("equipmentId"), """
                {"equipmentId": "WU-A01"}""")
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.shiftLabel").value("IT-SHIFT"));

        mockMvc.perform(get("/api/v1/objects/by-path/variables/detail")
                        .param("path", HUB)
                        .param("name", "pendingOutboxCount"))
                .andExpect(status().isOk());
    }

    private void deployBundle() throws Exception {
        String bundle = new ClassPathResource("erp-mes-core-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);
        mockMvc.perform(post("/api/v1/applications/erp-mes-core/deploy")
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

    private ResultActions invoke(String functionName, String schemaFields, String rowJson) throws Exception {
        String body = """
                {
                  "objectPath": "%s",
                  "functionName": "%s",
                  "input": {
                    "schema": { "name": "in", "fields": [ %s ] },
                    "rows": [ %s ]
                  }
                }
                """.formatted(HUB, functionName, schemaFields, rowJson);
        return mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}
