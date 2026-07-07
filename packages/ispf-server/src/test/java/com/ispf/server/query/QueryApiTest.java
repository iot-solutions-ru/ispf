package com.ispf.server.query;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "ispf.security.rbac-enabled=true")
class QueryApiTest {

    private static final String QUERY_PATH = "root.platform.queries.device-scan";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void queryCrud() throws Exception {
        mockMvc.perform(post("/api/v1/queries")
                        .header("X-ISPF-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "queryId": "device-scan",
                                  "displayName": "Device scan",
                                  "description": "Scan all devices",
                                  "queryType": "tree-scan",
                                  "sourcePathPattern": "root.platform.devices.*",
                                  "fieldsJson": "[{\\"name\\":\\"path\\",\\"source\\":\\"path\\"}]",
                                  "filterExpression": "",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queryId").value("device-scan"))
                .andExpect(jsonPath("$.queryType").value("tree-scan"));

        mockMvc.perform(get("/api/v1/queries/by-path")
                        .header("X-ISPF-Role", "admin")
                        .param("path", QUERY_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourcePathPattern").value("root.platform.devices.*"));

        mockMvc.perform(put("/api/v1/queries/by-path")
                        .header("X-ISPF-Role", "admin")
                        .param("path", QUERY_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Device scan v2",
                                  "description": "Updated",
                                  "queryType": "tree-scan",
                                  "sourcePathPattern": "root.platform.devices.**",
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(get("/api/v1/queries")
                        .header("X-ISPF-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.queryId == 'device-scan')]").exists());

        mockMvc.perform(delete("/api/v1/queries/by-path")
                        .header("X-ISPF-Role", "admin")
                        .param("path", QUERY_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("deleted"));
    }
}
