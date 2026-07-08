package com.ispf.server.federation;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
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
@Isolated
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "ispf.security.rbac-enabled=true",
        "ispf.security.secrets-key=test-secret-key-for-federation-phase7",
        "ispf.federation.outbound-buffer.max-bytes=65536",
        "ispf.agent.store-forward.persist-to-disk=false",
        "ispf.license.data-dir=${java.io.tmpdir}/ispf-federation-store-forward-${random.uuid}"
})
class FederationStoreForwardIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FederationTunnelAgentService tunnelAgentService;

    @Autowired
    private FederationOutboundEventBufferRegistry bufferRegistry;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    @Test
    void buffersEventsWhileDisconnectedAndReplaysOnReconnect() throws Exception {
        String token = loginAdmin();
        String baseUrl = "http://127.0.0.1:" + port;
        String siteName = "store-forward-" + System.nanoTime();
        String deviceName = "store-forward-buffer-" + System.nanoTime();
        String isolatedPath = ensureQuietDevice(deviceName);

        String registrationCode = createRegistration(token, siteName, isolatedPath);
        String agentId = createOutboundAgent(token, siteName, baseUrl, registrationCode, isolatedPath);
        UUID agentUuid = UUID.fromString(agentId);

        waitForConnected(token, agentId);

        tunnelAgentService.disconnect(agentUuid);
        Thread.sleep(500);

        for (int i = 0; i < 5; i++) {
            eventPublisher.publishEvent(ObjectChangeEvent.variableUpdated(
                    isolatedPath,
                    "temperature"
            ));
        }

        assertThat(bufferRegistry.stats(agentUuid).count()).isEqualTo(5);

        tunnelAgentService.scheduleConnect(agentUuid);
        waitForConnected(token, agentId);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(
                FederationIntegrationTestSupport.BUFFER_DRAIN_TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (bufferRegistry.stats(agentUuid).count() == 0) {
                return;
            }
            Thread.sleep(500);
        }

        assertThat(bufferRegistry.stats(agentUuid).count()).isZero();
    }

    private String loginAdmin() throws Exception {
        return FederationIntegrationTestSupport.loginAdmin(mockMvc);
    }

    private String createRegistration(String token, String siteName, String pathPrefix) throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/v1/federation/inbound/registrations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "pathPrefix": "%s"
                                }
                                """.formatted(siteName, pathPrefix)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(registration.getResponse().getContentAsString())
                .path("registrationCode")
                .asString();
    }

    private String createOutboundAgent(String token, String siteName, String baseUrl, String registrationCode,
            String pathPrefix)
            throws Exception {
        MvcResult agentCreated = mockMvc.perform(post("/api/v1/federation/outbound/agents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "hubBaseUrl": "%s",
                                  "registrationCode": "%s",
                                  "pathPrefix": "%s"
                                }
                                """.formatted(siteName, baseUrl, registrationCode, pathPrefix)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(agentCreated.getResponse().getContentAsString()).path("id").asString();
    }

    private void waitForConnected(String token, String agentId) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(
                FederationIntegrationTestSupport.TUNNEL_CONNECT_TIMEOUT_SECONDS);
        long nextConnectRetryAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        String lastStatus = null;
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
                lastStatus = agent.path("tunnelStatus").asString(null);
                if (FederationIntegrationTestSupport.shouldRetryConnect(lastStatus)
                        && System.nanoTime() >= nextConnectRetryAt) {
                    mockMvc.perform(post("/api/v1/federation/outbound/agents/" + agentId + "/connect")
                                    .header("Authorization", "Bearer " + token))
                            .andExpect(status().isOk());
                    nextConnectRetryAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(
                            FederationIntegrationTestSupport.CONNECT_RETRY_INTERVAL_MS);
                }
                if ("CONNECTED".equals(lastStatus)) {
                    return;
                }
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException(
                "Timed out waiting for outbound agent connect: " + agentId + " (lastStatus=" + lastStatus + ")");
    }

    private String ensureQuietDevice(String deviceName) {
        String path = "root.platform.devices." + deviceName;
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(
                    "root.platform.devices",
                    deviceName,
                    ObjectType.DEVICE,
                    deviceName,
                    "",
                    null
            );
            DataSchema temperature = DataSchema.builder("temperature")
                    .field("value", FieldType.DOUBLE)
                    .field("unit", FieldType.STRING)
                    .build();
            objectManager.createVariable(
                    path,
                    "temperature",
                    temperature,
                    true,
                    true,
                    DataRecord.single(temperature, java.util.Map.of("value", 20.0, "unit", "C")),
                    false,
                    null
            );
        }
        return path;
    }
}
