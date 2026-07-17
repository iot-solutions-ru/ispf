package com.ispf.server.ai.audit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentMetricsApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "admin")
    void adminCanReadTurnAndToolMetrics() throws Exception {
        mockMvc.perform(get("/api/v1/ai/agent/metrics").param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(7))
                .andExpect(jsonPath("$.turnsByStatus").exists())
                .andExpect(jsonPath("$.toolLatencyBreakdown").exists());

        mockMvc.perform(get("/api/v1/ai/agent/metrics/tools").param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(7))
                .andExpect(jsonPath("$.tools").isArray())
                .andExpect(jsonPath("$.toolCount").exists())
                .andExpect(jsonPath("$.totalCalls").exists());
    }

    @Test
    @WithMockUser(roles = "developer")
    void developerCannotReadToolMetrics() throws Exception {
        mockMvc.perform(get("/api/v1/ai/agent/metrics/tools"))
                .andExpect(status().isForbidden());
    }
}
