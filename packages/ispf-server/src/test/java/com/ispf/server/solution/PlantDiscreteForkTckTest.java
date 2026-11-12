package com.ispf.server.solution;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    private static final String HUB = "root.platform.singleton-blueprints.mes-platform-hub-v1";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void cleanInstallRejectsAnUnsignedPassportWrite() throws Exception {
        String bundle = Files.readString(plantBundle());

        mockMvc.perform(post("/api/v1/applications/plant-discrete/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoke("mes_platform_listLines", "{}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows", hasSize(0)));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoke("plant_set_lot_passport", """
                                {"lotId":"BATCH-PLANT-1","passportNote":"heat-42"}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Required field missing: signature")));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoke("plant_set_lot_passport", """
                                {"lotId":"BATCH-PLANT-1","passportNote":"heat-42","signature":""}
                                """)))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("requires a signature")));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoke("plant_set_lot_passport", """
                                {"lotId":"BATCH-PLANT-1","passportNote":"heat-42","signature":"planner-1"}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.lotId").value("BATCH-PLANT-1"));
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
