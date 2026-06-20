package com.ispf.server.federation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "ispf.security.rbac-enabled=true")
class FederationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @LocalServerPort
    private int port;

    @Test
    void adminManagesFederationPeers() throws Exception {
        String token = loginAdmin();
        String baseUrl = "http://127.0.0.1:" + port;

        mockMvc.perform(get("/api/v1/federation/peers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        MvcResult created = mockMvc.perform(post("/api/v1/federation/peers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "site-a",
                                  "baseUrl": "%s",
                                  "pathPrefix": "root.platform",
                                  "enabled": true,
                                  "description": "Local loopback peer for tests"
                                }
                                """.formatted(baseUrl)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("site-a"))
                .andExpect(jsonPath("$.hasAuthToken").value(false))
                .andReturn();

        String id = created.getResponse().getContentAsString()
                .replaceAll("(?s).*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/v1/federation/peers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='site-a')]").exists());

        mockMvc.perform(get("/api/v1/federation/proxy/objects/by-path")
                        .header("Authorization", "Bearer " + token)
                        .param("peerId", id)
                        .param("path", "devices.demo-sensor-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("root.platform.devices.demo-sensor-01"));

        mockMvc.perform(post("/api/v1/federation/peers/" + id + "/sync-catalog")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.localRoot").value("root.platform.federation.site-a"))
                .andExpect(jsonPath("$.created").value(greaterThan(0)));

        mockMvc.perform(get("/api/v1/objects/by-path")
                        .header("Authorization", "Bearer " + token)
                        .param("path", "root.platform.federation.site-a.devices.demo-sensor-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("root.platform.federation.site-a.devices.demo-sensor-01"))
                .andExpect(jsonPath("$.displayName").value("Demo Sensor 01"));

        mockMvc.perform(get("/api/v1/dashboards/by-path")
                        .header("Authorization", "Bearer " + token)
                        .param("path", "root.platform.federation.site-a.dashboards.demo-sensor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("root.platform.federation.site-a.dashboards.demo-sensor"))
                .andExpect(jsonPath("$.title").value("Demo Sensor Dashboard"))
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='temp-value')].objectPath")
                        .value("root.platform.federation.site-a.devices.demo-sensor-01"));

        mockMvc.perform(delete("/api/v1/federation/peers/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/federation/peers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='site-a')]").doesNotExist());
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
