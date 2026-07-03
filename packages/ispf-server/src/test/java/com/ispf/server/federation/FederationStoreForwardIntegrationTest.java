package com.ispf.server.federation;

import com.ispf.server.object.ObjectChangeEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "ispf.security.rbac-enabled=true",
        "ispf.security.secrets-key=test-secret-key-for-federation-phase7",
        "ispf.federation.outbound-buffer.max-bytes=65536"
})
class FederationStoreForwardIntegrationTest {

    private static final long TUNNEL_CONNECT_TIMEOUT_SECONDS =
            System.getenv("CI") != null ? 120 : 60;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FederationTunnelAgentService tunnelAgentService;

    @Autowired
    private FederationOutboundEventBufferRegistry bufferRegistry;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    @Test
    void buffersEventsWhileDisconnectedAndReplaysOnReconnect() throws Exception {
        String token = loginAdmin();
        String baseUrl = "http://127.0.0.1:" + port;
        String siteName = "store-forward-" + System.nanoTime();

        String registrationCode = createRegistration(token, siteName);
        String agentId = createOutboundAgent(token, siteName, baseUrl, registrationCode);
        UUID agentUuid = UUID.fromString(agentId);

        waitForConnected(token, agentId);

        tunnelAgentService.disconnect(agentUuid);
        Thread.sleep(500);

        for (int i = 0; i < 5; i++) {
            eventPublisher.publishEvent(ObjectChangeEvent.variableUpdated(
                    "root.platform.devices.demo-sensor-01",
                    "temperature"
            ));
        }

        assertThat(bufferRegistry.stats(agentUuid).count()).isGreaterThan(0);

        tunnelAgentService.scheduleConnect(agentUuid);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TUNNEL_CONNECT_TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (bufferRegistry.stats(agentUuid).count() == 0) {
                break;
            }
            Thread.sleep(500);
        }

        assertThat(bufferRegistry.stats(agentUuid).count()).isZero();
    }

    private String loginAdmin() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString()).path("token").asString();
    }

    private String createRegistration(String token, String siteName) throws Exception {
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
                .andReturn();
        return objectMapper.readTree(registration.getResponse().getContentAsString())
                .path("registrationCode")
                .asString();
    }

    private String createOutboundAgent(String token, String siteName, String baseUrl, String registrationCode)
            throws Exception {
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
        return objectMapper.readTree(agentCreated.getResponse().getContentAsString()).path("id").asString();
    }

    private void waitForConnected(String token, String agentId) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TUNNEL_CONNECT_TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            MvcResult agentsResult = mockMvc.perform(get("/api/v1/federation/outbound/agents")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode agents = objectMapper.readTree(agentsResult.getResponse().getContentAsString());
            for (JsonNode agent : agents) {
                if (agentId.equals(agent.path("id").asString(null))
                        && "CONNECTED".equals(agent.path("tunnelStatus").asString(null))) {
                    return;
                }
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("Timed out waiting for outbound agent connect: " + agentId);
    }
}
