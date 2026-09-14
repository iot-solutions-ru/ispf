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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ispf.security.rbac-enabled=true",
        "ispf.security.token-auth-enabled=true"
})
class AutomationObjectWriteAclApiTest {

    private static final String DEMO_DEVICE = "root.platform.devices.demo-sensor-01";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void developerCannotMutateAlertRuleWhenAclGrantsWriteOnlyToAdmin() throws Exception {
        String admin = login("admin", "admin");
        String developer = login("developer", "developer");
        String name = "ACL alert " + UUID.randomUUID().toString().substring(0, 8);

        MvcResult created = mockMvc.perform(post("/api/v1/alert-rules")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "objectPath": "%s",
                                  "watchVariable": "temperature",
                                  "conditionExpr": "self.temperature[\\"value\\"] > 999.0",
                                  "eventName": "aclAlertTest",
                                  "enabled": false,
                                  "edgeTrigger": false
                                }
                                """.formatted(name, DEMO_DEVICE)))
                .andExpect(status().isOk())
                .andReturn();
        String path = created.getResponse().getContentAsString()
                .replaceAll("(?s).*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

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

        mockMvc.perform(get("/api/v1/alert-rules/by-path")
                        .header("Authorization", "Bearer " + developer)
                        .param("path", path))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/alert-rules/by-path")
                        .header("Authorization", "Bearer " + developer)
                        .param("path", path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "enabled": true }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/alert-rules/by-path")
                        .header("Authorization", "Bearer " + developer)
                        .param("path", path))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/alert-rules/by-path")
                        .header("Authorization", "Bearer " + admin)
                        .param("path", path))
                .andExpect(status().isOk());
    }

    @Test
    void developerCannotMutateEventFilterWhenAclGrantsWriteOnlyToAdmin() throws Exception {
        String admin = login("admin", "admin");
        String developer = login("developer", "developer");
        String filterId = "acl-filter-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String path = "root.platform.event-filters." + filterId;

        mockMvc.perform(post("/api/v1/event-filters")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "filterId": "%s",
                                  "displayName": "ACL filter",
                                  "enabled": false
                                }
                                """.formatted(filterId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value(path));

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

        mockMvc.perform(get("/api/v1/event-filters/by-path")
                        .header("Authorization", "Bearer " + developer)
                        .param("path", path))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/event-filters/by-path")
                        .header("Authorization", "Bearer " + developer)
                        .param("path", path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "enabled": true }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/event-filters/by-path")
                        .header("Authorization", "Bearer " + admin)
                        .param("path", path))
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
        return result.getResponse().getContentAsString()
                .replaceAll("(?s).*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }
}
