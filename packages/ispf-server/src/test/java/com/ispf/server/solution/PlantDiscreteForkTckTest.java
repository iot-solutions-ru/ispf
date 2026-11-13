package com.ispf.server.solution;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.server.application.data.ApplicationMessageRelay;
import com.ispf.server.application.data.HttpOutboxDelivery;
import com.ispf.server.application.function.ApplicationFunctionStore;
import com.ispf.server.application.script.Ed25519FunctionSignatureVerifier;
import com.ispf.server.application.script.FunctionSignature;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The plant fork of mes-platform installs without demo rows, and its passport
 * function is rejected until the caller supplies a signature.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlantDiscreteForkTckTest {

    private static final String HUB = "root.platform.singleton-blueprints.plant-discrete-hub-v1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationFunctionStore functionStore;

    @Autowired
    private Ed25519FunctionSignatureVerifier signatureVerifier;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationMessageRelay outbox;

    @Test
    void cleanInstallRejectsAnUnsignedPassportWrite() throws Exception {
        String bundle = Files.readString(plantBundle());

        // Anonymous deploy exercises FunctionTestRunner's rollback-only test principal.
        mockMvc.perform(post("/api/v1/applications/plant-discrete/deploy")
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoke("mes_platform_listLines", "{}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows", hasSize(0)));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoke("plant_set_lot_passport", """
                                {"lotId":"BATCH-PLANT-1","passportNote":"heat-42"}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Required field missing: signature")));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .with(user("planner-1").roles("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoke("plant_set_lot_passport", """
                                {"lotId":"BATCH-PLANT-1","passportNote":"heat-42","signature":""}
                                """)))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("requires a signature")));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoke("plant_set_lot_passport", """
                                {"lotId":"BATCH-PLANT-1","passportNote":"heat-42","signature":"planner-1"}
                                """)))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("authenticated principal")));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .with(user("planner-1").roles("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoke("plant_set_lot_passport", """
                                {"lotId":"BATCH-PLANT-1","passportNote":"heat-42","signature":"planner-1"}
                                """)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .with(user("planner-1").roles("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signedPassport()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.lotId").value("BATCH-PLANT-1"));
    }

    @Test
    void plantCycleEnqueuesOneErpDeliveryPerIdempotencyKey() throws Exception {
        String bundle = Files.readString(plantBundle());
        mockMvc.perform(post("/api/v1/applications/plant-discrete/deploy")
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle))
                .andExpect(status().isOk());

        jdbcTemplate.update("""
                INSERT INTO app_plant_discrete.mes_oee_shift (id, line_code, shift_label, planned_minutes, downtime_minutes, ideal_cycle_sec, total_units, good_units)
                VALUES ('dddddddd-dddd-dddd-dddd-dddddddddddd', 'LINE-A01', 'Morning', 480, 0, 60, 480, 480)
                """);
        jdbcTemplate.update("""
                INSERT INTO app_plant_discrete.mes_operation_status (wo_number, operation_id, status)
                VALUES ('WO-1', 'OP-10', 'ready')
                """);

        mockMvc.perform(post("/api/v1/bff/invoke").with(user("planner-1").roles("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invokeWith("mes_oee_getKpi",
                                "[{\"name\":\"shiftId\",\"type\":\"STRING\"}]",
                                "{\"shiftId\":\"dddddddd-dddd-dddd-dddd-dddddddddddd\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.oeePct").value(100));

        mockMvc.perform(post("/api/v1/bff/invoke").with(user("planner-1").roles("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invokeWith("mes_batch_runPhase",
                                "[{\"name\":\"batchPath\",\"type\":\"STRING\"},{\"name\":\"batchId\",\"type\":\"STRING\"},{\"name\":\"recipe\",\"type\":\"STRING\"},{\"name\":\"phase\",\"type\":\"STRING\"}]",
                                "{\"batchPath\":\"root.platform.mes.lots.batch-plant-1\",\"batchId\":\"BATCH-PLANT-1\",\"recipe\":\"recipe-standard-a\",\"phase\":\"react\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.phase").value("react"));

        mockMvc.perform(post("/api/v1/bff/invoke").with(user("planner-1").roles("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invokeWith("mes_quality_autoHold",
                                "[{\"name\":\"batchId\",\"type\":\"STRING\"},{\"name\":\"measurement\",\"type\":\"DOUBLE\"},{\"name\":\"ucl\",\"type\":\"DOUBLE\"},{\"name\":\"lcl\",\"type\":\"DOUBLE\"},{\"name\":\"reason\",\"type\":\"STRING\"}]",
                                "{\"batchId\":\"BATCH-PLANT-1\",\"measurement\":99,\"ucl\":10,\"lcl\":0,\"reason\":\"spc\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.phase").value("hold"));

        mockMvc.perform(post("/api/v1/bff/invoke").with(user("planner-1").roles("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invokeWith("mes_quality_release",
                                "[{\"name\":\"batchId\",\"type\":\"STRING\"},{\"name\":\"measurement\",\"type\":\"DOUBLE\"},{\"name\":\"ucl\",\"type\":\"DOUBLE\"},{\"name\":\"lcl\",\"type\":\"DOUBLE\"}]",
                                "{\"batchId\":\"BATCH-PLANT-1\",\"measurement\":5,\"ucl\":10,\"lcl\":0}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.phase").value("charge"));

        mockMvc.perform(post("/api/v1/bff/invoke").with(user("planner-1").roles("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invokeWith("mes_bom_explode",
                                "[{\"name\":\"materialCode\",\"type\":\"STRING\"}]",
                                "{\"materialCode\":\"MAT-WIDGET-A01\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"));

        mockMvc.perform(post("/api/v1/bff/invoke").with(user("planner-1").roles("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invokeWith("mes_genealogy_queryByLot",
                                "[{\"name\":\"lotId\",\"type\":\"STRING\"}]",
                                "{\"lotId\":\"BATCH-PLANT-1\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"));

        mockMvc.perform(post("/api/v1/bff/invoke").with(user("planner-1").roles("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invokeWith("mes_ops_complete",
                                "[{\"name\":\"workOrderNumber\",\"type\":\"STRING\"},{\"name\":\"operationId\",\"type\":\"STRING\"}]",
                                "{\"workOrderNumber\":\"WO-1\",\"operationId\":\"OP-10\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"));

        String workOrder = "root.platform.mes.work-orders.wo-line-a01-001";
        mockMvc.perform(post("/api/v1/bff/invoke").with(user("planner-1").roles("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invokeWith("mes_dispatch_confirmWorkOrder",
                                "[{\"name\":\"workOrderPath\",\"type\":\"STRING\"},{\"name\":\"operatorId\",\"type\":\"STRING\"}]",
                                "{\"workOrderPath\":\"" + workOrder + "\",\"operatorId\":\"planner-1\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.confirmed").value(true));

        mockMvc.perform(post("/api/v1/bff/invoke").with(user("planner-1").roles("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signedPassport()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.lotId").value("BATCH-PLANT-1"));

        mockMvc.perform(post("/api/v1/bff/invoke").with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invokeWith("mes_erp_pollOutbox", "[]", "{}")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("pending")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("simulated"))));

        AtomicInteger hits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/inbox", exchange -> {
            hits.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            HttpOutboxDelivery delivery = new HttpOutboxDelivery(HttpOutboxDelivery.parseEndpoints(
                    "erp=http://127.0.0.1:" + server.getAddress().getPort() + "/inbox"));
            assertEquals(2, outbox.relayOnce(Set.of("erp"), delivery));
            mockMvc.perform(post("/api/v1/bff/invoke").with(anonymous())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invokeWith("mes_erp_pollOutbox", "[]", "{}")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("delivered")));
            assertEquals(0, outbox.relayOnce(Set.of("erp"), delivery));
            mockMvc.perform(post("/api/v1/bff/invoke").with(user("planner-1").roles("operator"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invokeWith("mes_dispatch_confirmWorkOrder",
                                    "[{\"name\":\"workOrderPath\",\"type\":\"STRING\"},{\"name\":\"operatorId\",\"type\":\"STRING\"}]",
                                    "{\"workOrderPath\":\"" + workOrder + "\",\"operatorId\":\"planner-1\"}")))
                    .andExpect(status().isOk());
            assertEquals(0, outbox.relayOnce(Set.of("erp"), delivery));
            assertEquals(2, hits.get());
        } finally {
            server.stop(0);
        }

        Integer orders = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.application_outbox WHERE message_id = ?",
                Integer.class,
                "WORK_ORDER:" + workOrder
        );
        assertEquals(1, orders);

        mockMvc.perform(post("/api/v1/applications/plant-discrete/outbox")
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectorId\":\"erp\",\"messageId\":\"API-1\",\"body\":\"{}\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/applications/plant-discrete/outbox")
                        .with(anonymous())
                        .param("connectorId", "erp")
                        .param("messageId", "API-1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/applications/plant-discrete/outbox")
                        .with(user("planner-1").roles("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectorId\":\"erp\",\"messageId\":\"API-1\",\"body\":\"{}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.status").value("PENDING"));
        mockMvc.perform(post("/api/v1/applications/plant-discrete/outbox")
                        .with(user("planner-1").roles("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectorId\":\"erp\",\"messageId\":\"API-1\",\"body\":\"{}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false));
        mockMvc.perform(get("/api/v1/applications/plant-discrete/outbox")
                        .with(user("planner-1").roles("admin"))
                        .param("connectorId", "erp")
                        .param("messageId", "API-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    private String signedPassport() throws Exception {
        var deployed = functionStore.findLatest(HUB, "plant_set_lot_passport").orElseThrow();
        DataSchema schema = new ObjectMapper().readValue(deployed.inputSchemaJson(), DataSchema.class);
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        signatureVerifier.enroll("planner-1", Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("lotId", "BATCH-PLANT-1");
        row.put("passportNote", "heat-42");
        row.put("signature", "pending");
        byte[] message = FunctionSignature.message(
                "REQUIRED", deployed.sourceBody(), "planner-1", "", DataRecord.single(schema, row));
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(message);
        String signature = Base64.getEncoder().encodeToString(signer.sign());
        return invoke("plant_set_lot_passport", """
                {"lotId":"BATCH-PLANT-1","passportNote":"heat-42","signature":"%s"}
                """.formatted(signature));
    }

    private static String invokeWith(String functionName, String fieldsJson, String rowJson) {
        return """
                {
                  "objectPath": "%s",
                  "functionName": "%s",
                  "input": {
                    "schema": { "name": "in", "fields": %s },
                    "rows": [%s]
                  }
                }
                """.formatted(HUB, functionName, fieldsJson, rowJson);
    }

    private static String invoke(String functionName, String rowJson) {
        return """
                {
                  "objectPath": "%s",
                  "functionName": "%s",
                  "input": {
                    "schema": {
                      "name": "in",
                      "fields": [
                        {"name": "lotId", "type": "STRING"},
                        {"name": "passportNote", "type": "STRING"},
                        {"name": "signature", "type": "STRING"}
                      ]
                    },
                    "rows": [%s]
                  }
                }
                """.formatted(HUB, functionName, rowJson);
    }

    private static Path plantBundle() {
        Path cwd = Path.of("").toAbsolutePath();
        Path[] candidates = {
                cwd.resolve("examples/plant-discrete-mes/bundle.json"),
                cwd.resolve("../../examples/plant-discrete-mes/bundle.json").normalize()
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        assertTrue(false, "plant bundle not found from " + cwd);
        return candidates[0];
    }
}
