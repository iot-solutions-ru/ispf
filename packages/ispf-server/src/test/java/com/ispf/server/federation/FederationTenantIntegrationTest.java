package com.ispf.server.federation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ispf.security.rbac-enabled=true",
        "ispf.security.token-auth-enabled=true"
})
class FederationTenantIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @LocalServerPort
    private int port;

    @Test
    void tenantOperatorCannotManagePeersOrProxyPlatformFederation() throws Exception {
        String adminToken = login("admin", "admin");

        mockMvc.perform(post("/api/v1/tenants")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "fedco",
                                  "displayName": "Fed Co",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/v1/tenants/fedco/users/operator")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        String operatorToken = login("operator", "operator");
        String baseUrl = "http://127.0.0.1:" + port;

        MvcResult created = mockMvc.perform(post("/api/v1/federation/peers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "fed-site",
                                  "baseUrl": "%s",
                                  "pathPrefix": "root.platform",
                                  "enabled": true
                                }
                                """.formatted(baseUrl)))
                .andExpect(status().isOk())
                .andReturn();

        String peerId = created.getResponse().getContentAsString()
                .replaceAll("(?s).*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/v1/federation/peers")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/federation/proxy/objects/by-path")
                        .header("Authorization", "Bearer " + operatorToken)
                        .param("peerId", peerId)
                        .param("path", "devices.demo-sensor-01"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/federation/proxy/objects/by-path")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("peerId", peerId)
                        .param("path", "devices.demo-sensor-01"))
                .andExpect(status().isOk());
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
