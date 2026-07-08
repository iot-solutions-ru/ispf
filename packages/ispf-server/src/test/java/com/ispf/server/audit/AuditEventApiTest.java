package com.ispf.server.audit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ispf.security.rbac-enabled=true",
        "ispf.security.token-auth-enabled=true"
})
class AuditEventApiTest {

    private static final String DEVICE = "root.platform.devices.demo-sensor-01";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginAndVariableAclChangesAreAudited() throws Exception {
        String adminToken = login("admin", "admin");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "operator", "password": "operator" }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/objects/by-path/variables")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("path", DEVICE)
                        .param("name", "temperature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "readRoles": ["operator"], "writeRoles": ["admin"] }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/audit/events")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("category", "auth")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action", hasItem("login.success")));

        mockMvc.perform(get("/api/v1/audit/events")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("category", "acl")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action", hasItem("variable.acl.updated")));
    }

    @Test
    void loginFailureIsAudited() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "nobody", "password": "wrong" }
                                """))
                .andExpect(status().isUnauthorized());

        String adminToken = login("admin", "admin");
        mockMvc.perform(get("/api/v1/audit/events")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("category", "auth")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action", hasItem("login.failure")));
    }

    @Test
    void objectDeleteIsAudited() throws Exception {
        String adminToken = login("admin", "admin");
        String path = "root.platform.devices.audit-delete-test";
        mockMvc.perform(post("/api/v1/objects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentPath": "root.platform.devices",
                                  "name": "audit-delete-test",
                                  "type": "DEVICE",
                                  "description": "audit delete test"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/objects/by-path")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("path", path))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/audit/events")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("category", "object")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action", hasItem("object.deleted")))
                .andExpect(jsonPath("$[*].targetId", hasItem(path)));
    }

    @Test
    void auditEndpointRequiresAdmin() throws Exception {
        String operatorToken = login("operator", "operator");

        mockMvc.perform(get("/api/v1/audit/events")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditExportReturnsCsv() throws Exception {
        String adminToken = login("admin", "admin");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "operator", "password": "operator" }
                                """))
                .andExpect(status().isOk());

        MvcResult export = mockMvc.perform(get("/api/v1/audit/events/export")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("category", "auth")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andReturn();

        String body = export.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body)
                .startsWith("id,category,action,actor,target_type,target_id,occurred_at,details_json")
                .contains("login.success");
        org.assertj.core.api.Assertions.assertThat(export.getResponse().getContentType())
                .contains("text/csv");
    }

    private String login(String username, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "%s", "password": "%s" }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return login.getResponse().getContentAsString()
                .replaceAll("(?s).*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }
}
