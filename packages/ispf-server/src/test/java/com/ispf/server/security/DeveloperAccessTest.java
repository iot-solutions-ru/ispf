package com.ispf.server.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "ispf.security.rbac-enabled=true")
class DeveloperAccessTest {

    private static final String DATA_SOURCE_PATH = "root.platform.data-sources.sql-editor-test";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "developer")
    void developerCanCreateChildObject() throws Exception {
        mockMvc.perform(post("/api/v1/objects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentPath": "root.platform.devices",
                                  "name": "dev-probe-device",
                                  "type": "DEVICE",
                                  "displayName": "Dev probe"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("root.platform.devices.dev-probe-device"));
    }

    @Test
    @WithMockUser(roles = "developer")
    void developerCannotAccessPlatformMetrics() throws Exception {
        mockMvc.perform(get("/api/v1/platform/metrics"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "developer")
    void developerCannotManageSecurityUsers() throws Exception {
        mockMvc.perform(get("/api/v1/security/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "developer")
    void developerCanInvokeExecuteQueryDirectly() throws Exception {
        mockMvc.perform(post("/api/v1/platform/packages/import")
                        .param("packageId", "sql-editor-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "1.0.0",
                                  "displayName": "SQL Editor Test",
                                  "schemaName": "app_sql_editor_test",
                                  "migrations": []
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/objects/by-path/functions/invoke")
                        .param("path", DATA_SOURCE_PATH)
                        .param("name", "executeQuery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rows": [{
                                    "query": "SELECT 3 AS n"
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].kind").value("rows"))
                .andExpect(jsonPath("$.rows[0].rowCount").value(1));
    }

    @Test
    @WithMockUser(roles = "developer")
    void developerCanAccessAiStudioApi() throws Exception {
        mockMvc.perform(get("/api/v1/ai/agent/tools"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "developer")
    void developerCannotAccessAgentMetrics() throws Exception {
        mockMvc.perform(get("/api/v1/ai/agent/metrics"))
                .andExpect(status().isForbidden());
    }
}
