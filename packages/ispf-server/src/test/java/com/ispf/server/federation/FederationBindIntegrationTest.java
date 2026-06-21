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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "ispf.security.rbac-enabled=true")
class FederationBindIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    @Test
    void bindRebindAndUnbindLocalObjectToRemotePeer() throws Exception {
        String token = loginAdmin();
        String baseUrl = "http://127.0.0.1:" + port;
        String peerName = "bind-peer-" + System.nanoTime();
        String localPath = "root.platform.devices.federation-bind-test-" + System.nanoTime();

        MvcResult peerCreated = mockMvc.perform(post("/api/v1/federation/peers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "baseUrl": "%s",
                                  "pathPrefix": "root.platform",
                                  "enabled": true
                                }
                                """.formatted(peerName, baseUrl)))
                .andExpect(status().isOk())
                .andReturn();
        String peerId = extractJsonField(peerCreated.getResponse().getContentAsString(), "id");

        mockMvc.perform(post("/api/v1/objects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentPath": "root.platform.devices",
                                  "name": "%s",
                                  "type": "DEVICE",
                                  "displayName": "Bind test device"
                                }
                                """.formatted(localPath.substring(localPath.lastIndexOf('.') + 1))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/federation/binds")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "localPath": "%s",
                                  "peerId": "%s",
                                  "remotePath": "root.platform.devices.demo-sensor-01"
                                }
                                """.formatted(localPath, peerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.localPath").value(localPath))
                .andExpect(jsonPath("$.bound").value(true))
                .andExpect(jsonPath("$.remotePath").value("root.platform.devices.demo-sensor-01"));

        mockMvc.perform(get("/api/v1/objects/by-path")
                        .header("Authorization", "Bearer " + token)
                        .param("path", localPath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value(localPath))
                .andExpect(jsonPath("$.federated").value(true))
                .andExpect(jsonPath("$.displayName").value("Demo Sensor 01"));

        mockMvc.perform(patch("/api/v1/federation/binds")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "localPath": "%s",
                                  "peerId": "%s",
                                  "remotePath": "root.platform.devices.demo-sensor-01"
                                }
                                """.formatted(localPath, peerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.localPath").value(localPath));

        mockMvc.perform(get("/api/v1/federation/binds")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.localPath=='" + localPath + "')]").exists());

        mockMvc.perform(delete("/api/v1/federation/binds")
                        .header("Authorization", "Bearer " + token)
                        .param("localPath", localPath))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/objects/by-path")
                        .header("Authorization", "Bearer " + token)
                        .param("path", localPath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value(localPath))
                .andExpect(jsonPath("$.federated").value(false))
                .andExpect(jsonPath("$.displayName").value("Demo Sensor 01"));

        mockMvc.perform(delete("/api/v1/federation/peers/" + peerId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void createAndBindAtLocalPath() throws Exception {
        String token = loginAdmin();
        String baseUrl = "http://127.0.0.1:" + port;
        String peerName = "create-bind-" + System.nanoTime();
        String name = "fed-device-" + System.nanoTime();
        String localPath = "root.platform.devices." + name;

        MvcResult peerCreated = mockMvc.perform(post("/api/v1/federation/peers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "baseUrl": "%s",
                                  "pathPrefix": "root.platform",
                                  "enabled": true
                                }
                                """.formatted(peerName, baseUrl)))
                .andExpect(status().isOk())
                .andReturn();
        String peerId = extractJsonField(peerCreated.getResponse().getContentAsString(), "id");

        mockMvc.perform(post("/api/v1/federation/binds")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentPath": "root.platform.devices",
                                  "name": "%s",
                                  "peerId": "%s",
                                  "remotePath": "root.platform.devices.demo-sensor-01"
                                }
                                """.formatted(name, peerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.localPath").value(localPath))
                .andExpect(jsonPath("$.type").value("DEVICE"));

        mockMvc.perform(get("/api/v1/objects/by-path")
                        .header("Authorization", "Bearer " + token)
                        .param("path", localPath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.federated").value(true));

        mockMvc.perform(delete("/api/v1/federation/peers/" + peerId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    private String loginAdmin() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "admin", "password": "admin" }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return extractJsonField(login.getResponse().getContentAsString(), "token");
    }

    private static String extractJsonField(String json, String field) {
        return json.replaceAll("(?s).*\\\"" + field + "\\\"\\s*:\\s*\\\"([^\\\"]+)\\\".*", "$1");
    }
}
