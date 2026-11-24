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
 * Plan execution contour on {@code mes-platform}: publish, shop response, fact, tail, replan.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Isolated
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MesPlanExecutionIntegrationTest {

    private static final String HUB = "root.platform.singleton-blueprints.mes-platform-hub-v1";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void officialPlanFactTailAndQualityBlock() throws Exception {
        deployBundle();

        mockMvc.perform(get("/api/v1/dashboards/by-path")
                        .param("path", "root.platform.dashboards.mes-platform-publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("MES Publish"));

        invoke("mes_plan_getDataReadiness", fields("siteCode"), row("siteCode", "PLANT-A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.status").value("partial"));

        invoke("mes_plan_ingestCandidate",
                fields("candidateId", "siteCode", "skuCode", "plannedQty", "uom", "qualityStatus", "idempotencyKey"),
                row("candidateId", "CAND-1", "siteCode", "PLANT-A", "skuCode", "MAT-WIDGET-A01",
                        "plannedQty", "100", "uom", "EA", "qualityStatus", "released", "idempotencyKey", "IF-04:CAND-1"))
                .andExpect(jsonPath("$.result.candidateId").value("CAND-1"));
        invoke("mes_plan_ingestCandidate",
                fields("candidateId", "siteCode", "skuCode", "plannedQty", "uom", "qualityStatus", "idempotencyKey"),
                row("candidateId", "CAND-OTHER", "siteCode", "PLANT-A", "skuCode", "MAT-WIDGET-A01",
                        "plannedQty", "1", "uom", "EA", "qualityStatus", "released", "idempotencyKey", "IF-04:CAND-1"))
                .andExpect(jsonPath("$.result.candidateId").value("CAND-1"));

        invoke("mes_plan_validate", fields("candidateId"), row("candidateId", "CAND-1"))
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.validationStatus").value("ready"));
        invoke("mes_plan_publish", fields("candidateId", "actor"), row("candidateId", "CAND-1", "actor", "planner"))
                .andExpect(jsonPath("$.result.pubId").value("PUB-CAND-1"))
                .andExpect(jsonPath("$.result.snapshotId").value("SNAP-CAND-1"));
        invoke("mes_plan_publish", fields("candidateId", "actor"), row("candidateId", "CAND-1", "actor", "planner"))
                .andExpect(jsonPath("$.result.pubId").value("PUB-CAND-1"));
        invoke("mes_plan_getPub", fields("pubId"), row("pubId", "PUB-CAND-1"))
                .andExpect(jsonPath("$.result.payloadJson").value(org.hamcrest.Matchers.containsString("\"candidateId\":\"CAND-1\"")))
                .andExpect(jsonPath("$.result.payloadJson").value(org.hamcrest.Matchers.containsString("\"skuCode\":\"MAT-WIDGET-A01\"")));

        invoke("mes_plan_ingestCandidate",
                fields("candidateId", "siteCode", "skuCode", "plannedQty", "uom", "qualityStatus", "idempotencyKey"),
                row("candidateId", "CAND-BLOCK", "siteCode", "PLANT-A", "skuCode", "MAT-BLOCKED",
                        "plannedQty", "10", "uom", "EA", "qualityStatus", "released", "idempotencyKey", "IF-04:BLOCK"))
                .andExpect(jsonPath("$.error_code").value("OK"));
        invoke("mes_plan_validate", fields("candidateId"), row("candidateId", "CAND-BLOCK"))
                .andExpect(jsonPath("$.error_code").value("BLOCKED"));
        invoke("mes_plan_publish", fields("candidateId", "actor"), row("candidateId", "CAND-BLOCK", "actor", "planner"))
                .andExpect(jsonPath("$.error_code").value("BLOCKED"));

        invoke("mes_plan_reserveSoft",
                fields("reserveId", "resourceId", "pubId", "qty"),
                row("reserveId", "RSV-1", "resourceId", "RES-CELL-01", "pubId", "PUB-CAND-1", "qty", "1"))
                .andExpect(jsonPath("$.error_code").value("BLOCKED"));
        invoke("mes_plan_reserveSoft",
                fields("reserveId", "resourceId", "pubId", "qty"),
                row("reserveId", "RSV-2", "resourceId", "RES-TANK-01", "pubId", "PUB-CAND-1", "qty", "25"))
                .andExpect(jsonPath("$.result.status").value("reserved"));

        invoke("mes_plan_rejectPub", fields("pubId", "reason"), row("pubId", "PUB-CAND-1", "reason", ""))
                .andExpect(jsonPath("$.error_code").value("BLOCKED"));
        invoke("mes_plan_acceptPub", fields("pubId", "actor"), row("pubId", "PUB-CAND-1", "actor", "shop"))
                .andExpect(jsonPath("$.result.shopStatus").value("accepted"));
        invoke("mes_plan_recordFact",
                fields("factId", "pubId", "actualQty", "delayMinutes", "reasonCode"),
                row("factId", "FACT-1", "pubId", "PUB-CAND-1", "actualQty", "40", "delayMinutes", "45", "reasonCode", "speed"))
                .andExpect(jsonPath("$.result.deviationId").value("FACT-1-D"));

        invoke("mes_plan_republish", fields("pubId", "reason", "actor"),
                row("pubId", "PUB-CAND-1", "reason", "line down", "actor", "planner"))
                .andExpect(jsonPath("$.result.pubId").value("PUB-CAND-1-R2"))
                .andExpect(jsonPath("$.result.previousPubId").value("PUB-CAND-1"));
        invoke("mes_plan_listDeliveries", fields("pubId"), row("pubId", "PUB-CAND-1-R2"))
                .andExpect(jsonPath("$.result.rows[?(@.recipientCode=='SHOP')].status").value("pending"))
                .andExpect(jsonPath("$.result.rows[?(@.recipientCode=='SPP')].status").value("pending"));
        invoke("mes_plan_planFact", fields("pubId"), row("pubId", "PUB-CAND-1"))
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.basis").value("comparable"));
        invoke("mes_plan_acceptPub", fields("pubId", "actor"), row("pubId", "PUB-CAND-1", "actor", "shop"))
                .andExpect(jsonPath("$.error_code").value("BLOCKED"));
        invoke("mes_plan_setDelivery",
                fields("pubId", "recipientCode", "status", "errorText"),
                row("pubId", "PUB-MISSING", "recipientCode", "SHOP", "status", "error", "errorText", "no row"))
                .andExpect(jsonPath("$.error_code").value("BLOCKED"));

        invoke("mes_plan_closePeriod",
                fields("pubId", "periodCode", "toleranceQty", "reasonCode"),
                row("pubId", "PUB-CAND-1", "periodCode", "D1", "toleranceQty", "10", "reasonCode", "short"))
                .andExpect(jsonPath("$.result.tailId").value("PUB-CAND-1:D1"))
                .andExpect(jsonPath("$.result.status").value("closed"));
        invoke("mes_plan_closePeriod",
                fields("pubId", "periodCode", "toleranceQty", "reasonCode"),
                row("pubId", "PUB-CAND-1", "periodCode", "D1", "toleranceQty", "10", "reasonCode", "short"))
                .andExpect(jsonPath("$.result.tailId").value("PUB-CAND-1:D1"));

        invoke("mes_plan_requestReplan",
                fields("alertId", "pubId", "siteCode", "reason"),
                row("alertId", "AL-1", "pubId", "PUB-CAND-1", "siteCode", "PLANT-A", "reason", "short"))
                .andExpect(jsonPath("$.result.idempotencyKey").value("IF-07:AL-1"));
        invoke("mes_plan_setDelivery",
                fields("pubId", "recipientCode", "status", "errorText"),
                row("pubId", "PUB-CAND-1", "recipientCode", "SHOP", "status", "error", "errorText", "bus down"))
                .andExpect(jsonPath("$.result.status").value("error"));
        invoke("mes_plan_listDeliveries", fields("pubId"), row("pubId", "PUB-CAND-1"))
                .andExpect(jsonPath("$.result.rows[?(@.recipientCode=='SHOP')].status").value("error"));
        invoke("mes_plan_getDataReadiness", fields("siteCode"), row("siteCode", "NO-SUCH-SITE"))
                .andExpect(jsonPath("$.result.status").value("empty"));

        invoke("mes_plan_if01_exportMaster", fields("siteCode"), row("siteCode", "PLANT-A"))
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.enqueued").value("true"));
        invoke("mes_plan_if08_ingestMaterialAvail",
                fields("messageId", "siteCode", "resourceId", "qty", "uom", "qualityStatus", "idempotencyKey"),
                row("messageId", "IF08-T1", "siteCode", "PLANT-A", "resourceId", "RES-TANK-01",
                        "qty", "99", "uom", "KG", "qualityStatus", "released", "idempotencyKey", "IF-08:IF08-T1"))
                .andExpect(jsonPath("$.result.status").value("received"));
        invoke("mes_plan_listOutbox", fields("entityType"), row("entityType", "IF-01"))
                .andExpect(jsonPath("$.error_code").value("OK"));
        invoke("mes_plan_listInbox", fields(), row())
                .andExpect(jsonPath("$.error_code").value("OK"));
    }

    private void deployBundle() throws Exception {
        String bundle = new ClassPathResource("mes-platform-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);
        mockMvc.perform(post("/api/v1/applications/mes-platform/deploy?withSeeds=true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));
    }

    private org.springframework.test.web.servlet.ResultActions invoke(String function, String fieldsJson, String rowJson) throws Exception {
        String body = """
                {
                  "objectPath": "%s",
                  "functionName": "%s",
                  "input": {
                    "schema": { "name": "in", "fields": %s },
                    "rows": [%s]
                  }
                }
                """.formatted(HUB, function, fieldsJson, rowJson);
        return mockMvc.perform(post("/api/v1/bff/invoke")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String fields(String... names) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < names.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append("{\"name\":\"").append(names[i]).append("\",\"type\":\"STRING\"}");
        }
        return builder.append(']').toString();
    }

    private static String row(String... pairs) {
        StringBuilder builder = new StringBuilder("{");
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('"').append(pairs[i]).append("\":\"").append(pairs[i + 1]).append('"');
        }
        return builder.append('}').toString();
    }
}
