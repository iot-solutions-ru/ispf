package com.ispf.server.tenant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class TenantApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsTenantNamespaceAndScopesOperatorTree() throws Exception {
        String adminToken = login("admin", "admin");

        mockMvc.perform(post("/api/v1/tenants")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "acme",
                                  "displayName": "Acme Corp",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformPath").value("root.tenant.acme.platform"));

        mockMvc.perform(putAssign(adminToken, "acme", "operator"))
                .andExpect(status().isOk());

        String operatorToken = login("operator", "operator");

        mockMvc.perform(get("/api/v1/objects")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].path", hasItem("root.tenant.acme.platform.devices")))
                .andExpect(jsonPath("$[*].path", not(hasItem("root.platform.devices"))));

        mockMvc.perform(get("/api/v1/objects")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].path", hasItem("root.platform.devices")));
    }

    private static org.springframework.test.web.servlet.RequestBuilder putAssign(
            String token,
            String tenantId,
            String username
    ) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                "/api/v1/tenants/" + tenantId + "/users/" + username
        ).header("Authorization", "Bearer " + token);
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
