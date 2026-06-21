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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ispf.security.rbac-enabled=true",
        "ispf.security.secrets-key=test-secret-key-for-federation-phase7"
})
class FederationTunnelIntegrationTest {

    private static final long TUNNEL_CONNECT_TIMEOUT_SECONDS = 60;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    @Test
    void outboundAgentConnectsAndProxiesThroughTunnel() throws Exception {
        String token = loginAdmin();
        String baseUrl = "http://127.0.0.1:" + port;
        String siteName = "tunnel-edge-" + System.nanoTime();

        MvcResult registration = mockMvc.perform(post("/api/v1/federation/inbound/registrations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "pathPrefix": "root.platform"
                                }
                                """.formatted(siteName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationCode").exists())
                .andReturn();

        String registrationCode = extractJsonField(
                registration.getResponse().getContentAsString(),
                "registrationCode"
        );

        MvcResult agentCreated = mockMvc.perform(post("/api/v1/federation/outbound/agents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "hubBaseUrl": "%s",
                                  "registrationCode": "%s",
                                  "pathPrefix": "root.platform"
                                }
                                """.formatted(siteName, baseUrl, registrationCode)))
                .andExpect(status().isOk())
                .andReturn();

        String agentId = extractJsonField(agentCreated.getResponse().getContentAsString(), "id");

        String peerId = waitForConnectedTunnelPeer(token, agentId, siteName);

        mockMvc.perform(get("/api/v1/federation/proxy/objects/by-path")
                        .header("Authorization", "Bearer " + token)
                        .param("peerId", peerId)
                        .param("path", "devices.demo-sensor-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("root.platform.devices.demo-sensor-01"));

        mockMvc.perform(get("/api/v1/federation/tunnels")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.peerId=='" + peerId + "')]").exists());

        mockMvc.perform(post("/api/v1/federation/peers/" + peerId + "/sync-catalog")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.localRoot").value("root.platform.federation." + siteName))
                .andExpect(jsonPath("$.created").value(greaterThan(0)));

        mockMvc.perform(get("/api/v1/objects/by-path")
                        .header("Authorization", "Bearer " + token)
                        .param("path", "root.platform.federation." + siteName + ".devices.demo-sensor-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Demo Sensor 01"));
    }

    private String waitForConnectedTunnelPeer(String token, String agentId, String siteName) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TUNNEL_CONNECT_TIMEOUT_SECONDS);
        String lastAgentStatus = null;
        String lastError = null;
        while (System.nanoTime() < deadline) {
            MvcResult agentsResult = mockMvc.perform(get("/api/v1/federation/outbound/agents")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode agents = objectMapper.readTree(agentsResult.getResponse().getContentAsString());
            for (JsonNode agent : agents) {
                if (!agentId.equals(agent.path("id").asString(null))) {
                    continue;
                }
                lastAgentStatus = agent.path("tunnelStatus").asString(null);
                lastError = agent.path("lastError").asString(null);
                if ("FAILED".equals(lastAgentStatus)) {
                    throw new IllegalStateException("Outbound agent failed: " + lastError);
                }
                JsonNode linkedPeerIdNode = agent.get("linkedPeerId");
                if ("CONNECTED".equals(lastAgentStatus)
                        && linkedPeerIdNode != null
                        && !linkedPeerIdNode.isNull()) {
                    String peerId = linkedPeerIdNode.asString();
                    if (isPeerTunnelConnected(token, peerId)) {
                        return peerId;
                    }
                }
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException(String.format(
                "Timed out waiting for tunnel peer (agent=%s, site=%s, lastStatus=%s, lastError=%s)",
                agentId,
                siteName,
                lastAgentStatus,
                lastError
        ));
    }

    private boolean isPeerTunnelConnected(String token, String peerId) throws Exception {
        MvcResult peersResult = mockMvc.perform(get("/api/v1/federation/peers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode peers = objectMapper.readTree(peersResult.getResponse().getContentAsString());
        for (JsonNode peer : peers) {
            if (peerId.equals(peer.path("id").asString(null)) && peer.path("tunnelConnected").asBoolean(false)) {
                return true;
            }
        }
        return false;
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
