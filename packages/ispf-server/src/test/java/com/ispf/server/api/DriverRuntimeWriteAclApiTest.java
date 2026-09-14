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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Device-scoped driver mutations must honor object WRITE ACL, not only tenant scope + CONFIG role.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ispf.security.rbac-enabled=true",
        "ispf.security.token-auth-enabled=true"
})
class DriverRuntimeWriteAclApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void developerCannotConfigureWhenObjectAclGrantsWriteOnlyToAdmin() throws Exception {
        String admin = login("admin", "admin");
        String developer = login("developer", "developer");
        String name = "acl-drv-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String path = "root.platform.devices." + name;

        mockMvc.perform(post("/api/v1/objects")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentPath": "root.platform.devices",
                                  "name": "%s",
                                  "type": "DEVICE",
                                  "displayName": "Driver ACL device"
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

        mockMvc.perform(put("/api/v1/drivers/runtime/configure")
                        .header("Authorization", "Bearer " + developer)
                        .param("devicePath", path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "driverId": "virtual",
                                  "pollIntervalMs": 5000,
                                  "configuration": {},
                                  "pointMappings": {},
                                  "autoStart": false
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/drivers/runtime/start")
                        .header("Authorization", "Bearer " + developer)
                        .param("devicePath", path))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/drivers/runtime/configure")
                        .header("Authorization", "Bearer " + admin)
                        .param("devicePath", path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "driverId": "virtual",
                                  "pollIntervalMs": 5000,
                                  "configuration": {},
                                  "pointMappings": {},
                                  "autoStart": false
                                }
                                """))
                .andExpect(status().isOk());
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "%s", "password": "%s" }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return body.replaceAll("(?s).*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }
}
