package com.ispf.server.solution;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.server.application.function.ApplicationFunctionStore;
import com.ispf.server.application.script.Ed25519FunctionSignatureVerifier;
import com.ispf.server.application.script.FunctionSignature;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
                        .with(user("planner-1").roles("OPERATOR"))
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
                        .with(user("planner-1").roles("OPERATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoke("plant_set_lot_passport", """
                                {"lotId":"BATCH-PLANT-1","passportNote":"heat-42","signature":"planner-1"}
                                """)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .with(user("planner-1").roles("OPERATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signedPassport()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.lotId").value("BATCH-PLANT-1"));
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
