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

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The demo plant is the factory a real plant is forked from.
 * With seeds it shows LINE-A01. Without seeds the line table stays empty.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoPlantTckTest {

    private static final String HUB = "root.platform.singleton-blueprints.demo-plant-hub-v1";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void seedsShowTheDemoLine() throws Exception {
        String bundle = Files.readString(demoBundle());

        mockMvc.perform(post("/api/v1/applications/demo-plant/deploy?withSeeds=true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "mes_platform_listLines",
                                  "input": { "schema": { "name": "in", "fields": [] }, "rows": [{}] }
                                }
                                """.formatted(HUB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows", hasSize(1)))
                .andExpect(jsonPath("$.result.rows[0].lineCode").value("LINE-A01"));
    }

    private static Path demoBundle() {
        Path cwd = Path.of("").toAbsolutePath();
        Path[] candidates = {
                cwd.resolve("examples/demo-plant/bundle.json"),
                cwd.resolve("../../examples/demo-plant/bundle.json").normalize()
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        assertTrue(false, "demo plant bundle not found from " + cwd);
        return candidates[0];
    }
}
