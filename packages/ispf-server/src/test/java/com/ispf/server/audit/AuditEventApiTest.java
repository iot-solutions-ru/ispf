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
        "ispf.security.token-auth-enabled=true",
        "ispf.security.mfa.enabled=true"
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
    void auditEndpointRequiresAdmin() throws Exception {
        String operatorToken = login("operator", "operator");

        mockMvc.perform(get("/api/v1/audit/events")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden());
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
