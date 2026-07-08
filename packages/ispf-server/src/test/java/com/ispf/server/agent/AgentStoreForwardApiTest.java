package com.ispf.server.agent;

import com.ispf.server.federation.FederationIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Isolated
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@TestPropertySource(properties = {
        "ispf.agent.store-forward.persist-to-disk=false",
        "ispf.license.data-dir=${java.io.tmpdir}/ispf-agent-store-forward-${random.uuid}"
})
class AgentStoreForwardApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgentStoreForwardService storeForwardService;

    @Test
    void statsEndpointReturnsAggregateBufferMetrics() throws Exception {
        UUID agentId = UUID.randomUUID();
        storeForwardService.enqueue(agentId, "root.platform.devices.a", "temperature", Instant.parse("2026-01-01T00:00:00Z"));
        storeForwardService.enqueue(agentId, "root.platform.devices.a", "pressure", Instant.parse("2026-01-01T00:01:00Z"));

        String token = FederationIntegrationTestSupport.loginAdmin(mockMvc);

        mockMvc.perform(get("/api/v1/agent/store-forward/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.totalPending").value(2))
                .andExpect(jsonPath("$.agents['" + agentId + "'].pendingCount").value(2))
                .andExpect(jsonPath("$.capturedAt").exists());
    }
}
