package com.ispf.server.federation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "ispf.security.rbac-enabled=true")
class FederationChaosIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    @Test
    void disabledPeerBlocksProxyAndRecoversAfterReEnable() throws Exception {
        String token = loginAdmin();
        String baseUrl = "http://127.0.0.1:" + port;

        MvcResult created = mockMvc.perform(post("/api/v1/federation/peers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "chaos-site",
                                  "baseUrl": "%s",
                                  "pathPrefix": "root.platform",
                                  "enabled": true
                                }
                                """.formatted(baseUrl)))
                .andExpect(status().isOk())
                .andReturn();

        String id = created.getResponse().getContentAsString()
                .replaceAll("(?s).*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/v1/federation/proxy/objects/by-path")
                        .header("Authorization", "Bearer " + token)
                        .param("peerId", id)
                        .param("path", "devices.demo-sensor-01"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/federation/peers/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "chaos-site",
                                  "baseUrl": "%s",
                                  "pathPrefix": "root.platform",
                                  "enabled": false
                                }
                                """.formatted(baseUrl)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(get("/api/v1/federation/proxy/objects/by-path")
                        .header("Authorization", "Bearer " + token)
                        .param("peerId", id)
                        .param("path", "devices.demo-sensor-01"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/federation/peers/" + id + "/sync-catalog")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/federation/peers/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "chaos-site",
                                  "baseUrl": "%s",
                                  "pathPrefix": "root.platform",
                                  "enabled": true
                                }
                                """.formatted(baseUrl)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        mockMvc.perform(post("/api/v1/federation/peers/" + id + "/sync-catalog")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    private String loginAdmin() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "admin", "password": "admin" }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return login.getResponse().getContentAsString()
                .replaceAll("(?s).*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }
}
