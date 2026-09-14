package com.ispf.server.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ispf.security.rbac-enabled=true",
        "ispf.security.token-auth-enabled=true"
})
class DataSourceSqlBindingWriteAclApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void developerCannotMutateDataSourceWhenAclGrantsWriteOnlyToAdmin() throws Exception {
        String admin = login("admin", "admin");
        String developer = login("developer", "developer");
        String name = "acl-ds-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String path = "root.platform.data-sources." + name;

        mockMvc.perform(post("/api/v1/objects")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentPath": "root.platform.data-sources",
                                  "name": "%s",
                                  "type": "DATA_SOURCE",
                                  "displayName": "ACL data source"
                                }
                                """.formatted(name)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/objects/by-path/acl")
                        .header("Authorization", "Bearer " + admin)
                        .param("path", path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "entries": [
                                    { "principalType": "ROLE", "principalId": "admin", "permission": "OWNER" },
                                    { "principalType": "ROLE", "principalId": "developer", "permission": "READ" }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/data-sources/by-path")
                        .header("Authorization", "Bearer " + developer)
                        .param("path", path))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/data-sources/by-path")
                        .header("Authorization", "Bearer " + developer)
                        .param("path", path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "displayName": "Denied rename" }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/data-sources/by-path/execute-query")
                        .header("Authorization", "Bearer " + developer)
                        .param("path", path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "query": "SELECT 1" }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/data-sources/by-path")
                        .header("Authorization", "Bearer " + admin)
                        .param("path", path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "displayName": "Admin rename" }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void developerCannotRefreshSqlBindingWhenAclGrantsWriteOnlyToAdmin() throws Exception {
        String admin = login("admin", "admin");
        String developer = login("developer", "developer");
        String bindingId = "acl-bind-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String path = "root.platform.bindings." + bindingId;

        mockMvc.perform(post("/api/v1/sql-bindings")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bindingId": "%s",
                                  "targetObjectPath": "root.platform.devices.demo-sensor-01",
                                  "variable": "temperature",
                                  "dataSourcePath": "root.platform.data-sources.platform-test",
                                  "query": "SELECT 1 AS value",
                                  "valueField": "value",
                                  "refresh": "manual",
                                  "enabled": false
                                }
                                """.formatted(bindingId)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/objects/by-path/acl")
                        .header("Authorization", "Bearer " + admin)
                        .param("path", path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "entries": [
                                    { "principalType": "ROLE", "principalId": "admin", "permission": "OWNER" },
                                    { "principalType": "ROLE", "principalId": "developer", "permission": "READ" }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/sql-bindings/by-path")
                        .header("Authorization", "Bearer " + developer)
                        .param("path", path))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/sql-bindings/by-path/refresh")
                        .header("Authorization", "Bearer " + developer)
                        .param("path", path))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/sql-bindings/by-path")
                        .header("Authorization", "Bearer " + developer)
                        .param("path", path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bindingId": "%s",
                                  "enabled": false
                                }
                                """.formatted(bindingId)))
                .andExpect(status().isForbidden());
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "%s", "password": "%s" }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString()
                .replaceAll("(?s).*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }
}
