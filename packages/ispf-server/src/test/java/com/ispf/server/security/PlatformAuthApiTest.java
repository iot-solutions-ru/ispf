package com.ispf.server.security;

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
class PlatformAuthApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginAndAccessApiWithBearerToken() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "admin", "password": "admin" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.roles", hasItem("admin")))
                .andReturn();

        String body = login.getResponse().getContentAsString();
        String token = body.replaceAll("(?s).*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/v1/objects")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/objects"))
                .andExpect(status().isForbidden());
    }

    @Test
    void syncsUsersIntoObjectTree() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "admin", "password": "admin" }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String body = login.getResponse().getContentAsString();
        String token = body.replaceAll("(?s).*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/v1/objects")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].path", hasItem("root.platform.security.users.admin")))
                .andExpect(jsonPath("$[*].path", hasItem("root.platform.security.roles.operator")));
    }

    @Test
    void adminIssuesFederationTokenForUser() throws Exception {
        String admin = adminToken();

        mockMvc.perform(post("/api/v1/security/users/operator/federation-token")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"ttlHours\": 24 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("operator"))
                .andExpect(jsonPath("$.purpose").value("federation"))
                .andExpect(jsonPath("$.ttlHours").value(24))
                .andExpect(jsonPath("$.token").exists());

        MvcResult issued = mockMvc.perform(post("/api/v1/security/users/operator/federation-token")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        String federationToken = issued.getResponse().getContentAsString()
                .replaceAll("(?s).*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/v1/security/users")
                        .header("Authorization", "Bearer " + federationToken))
                .andExpect(status().isOk());
    }

    @Test
    void loginReturnsTimeZone() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "admin", "password": "admin" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("UTC"));
    }

    @Test
    void operatorUpdatesOwnTimeZone() throws Exception {
        String token = adminToken();
        mockMvc.perform(put("/api/v1/auth/me/timezone")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "timeZone": "Europe/Moscow" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("Europe/Moscow"));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("Europe/Moscow"));
    }

    @Test
    void loginReturnsAutoStartPreferences() throws Exception {
        mockMvc.perform(put("/api/v1/security/users/operator")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "autoStartEnabled": true,
                                  "autoStartApp": "demo"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "operator", "password": "operator" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoStartEnabled").value(true))
                .andExpect(jsonPath("$.autoStartApp").value("demo"));
    }

    @Test
    void operatorAppsApiListsPlatformAndReturnsUi() throws Exception {
        String token = adminToken();

        mockMvc.perform(get("/api/v1/operator-apps")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].appId", hasItem("platform")));

        mockMvc.perform(get("/api/v1/operator-apps/platform/ui")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appId").value("platform"))
                .andExpect(jsonPath("$.defaultDashboard").value("root.platform.dashboards.snmp-host-monitoring"))
                .andExpect(jsonPath("$.dashboards").isArray());
    }

    @Test
    void managesPlatformRoles() throws Exception {
        String token = adminToken();

        mockMvc.perform(get("/api/v1/security/roles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("admin")))
                .andExpect(jsonPath("$[*].name", hasItem("operator")));

        mockMvc.perform(post("/api/v1/security/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "supervisor",
                                  "displayName": "Supervisor",
                                  "description": "Shift supervisor"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("supervisor"))
                .andExpect(jsonPath("$.objectPath").value("root.platform.security.roles.supervisor"));

        mockMvc.perform(get("/api/v1/objects")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].path", hasItem("root.platform.security.roles.supervisor")));

        mockMvc.perform(delete("/api/v1/security/roles/supervisor")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private String adminToken() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "admin", "password": "admin" }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String body = login.getResponse().getContentAsString();
        return body.replaceAll("(?s).*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }
}
