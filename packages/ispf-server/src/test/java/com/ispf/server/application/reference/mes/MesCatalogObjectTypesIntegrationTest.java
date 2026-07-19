package com.ispf.server.application.reference.mes;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-164: MES object types appear in the tree after {@code mes-platform} marketplace deploy
 * (not base platform seed).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MesCatalogObjectTypesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void mesPlatformDeploySeedsTypedCatalogAndIsa95Line() throws Exception {
        deployBundle();

        mockMvc.perform(get("/api/v1/objects/by-path").param("path", "root.platform.mes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("MES"));

        mockMvc.perform(get("/api/v1/objects/by-path")
                        .param("path", "root.platform.mes.work-orders.wo-line-a01-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("WORK_ORDER"))
                .andExpect(jsonPath("$.displayName").value("WO-LINE-A01-001"));

        mockMvc.perform(get("/api/v1/objects/by-path")
                        .param("path", "root.platform.mes.operations.op-assemble-a01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("OPERATION"));

        mockMvc.perform(get("/api/v1/objects/by-path")
                        .param("path", "root.platform.mes.lots.batch-line-a01-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("LOT"));

        mockMvc.perform(get("/api/v1/objects/by-path")
                        .param("path", "root.platform.mes.shifts.shift-morning-a01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SHIFT"));

        mockMvc.perform(get("/api/v1/objects/by-path")
                        .param("path", "root.platform.mes.quality-records.qr-line-a01-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("QUALITY_RECORD"));

        mockMvc.perform(get("/api/v1/objects/by-path")
                        .param("path", "root.platform.mes.instances.plant-a.areas.assembly.lines.line-a01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("LINE-A01"));
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
}
