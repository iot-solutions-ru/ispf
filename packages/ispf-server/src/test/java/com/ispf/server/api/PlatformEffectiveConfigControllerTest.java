package com.ispf.server.api;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.EventJournalProperties;
import com.ispf.server.config.HistorianTierProperties;
import com.ispf.server.config.ObjectChangeProperties;
import com.ispf.server.config.RuntimeTelemetryProperties;
import com.ispf.server.config.VariableHistoryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlatformEffectiveConfigControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        environment.setProperty(PlatformEffectiveConfigController.DEPLOY_PROFILE_PROPERTY, "demo-idle");

        HistorianTierProperties historian = new HistorianTierProperties();
        historian.setDeployProfile("hot-only");

        mockMvc = MockMvcBuilders.standaloneSetup(new PlatformEffectiveConfigController(
                cluster(),
                new ObjectChangeProperties(),
                new RuntimeTelemetryProperties(),
                new VariableHistoryProperties(),
                new EventJournalProperties(),
                historian,
                environment
        )).build();
    }

    @Test
    void effectiveConfigExposesProfileAndResolvedPools() throws Exception {
        mockMvc.perform(get("/api/v1/platform/effective-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.replicaProfile").value("io"))
                .andExpect(jsonPath("$.profile.replicaCapabilities").isArray())
                .andExpect(jsonPath("$.profile.clusterEnabled").value(true))
                .andExpect(jsonPath("$.profile.deployProfile").value("demo-idle"))
                .andExpect(jsonPath("$.profile.historianDeployProfile").value("hot-only"))
                .andExpect(jsonPath("$.resolvedPools").isMap())
                .andExpect(jsonPath("$.resolvedPools.clusterScheduled.min").value(2))
                .andExpect(jsonPath("$.resolvedPools.clusterScheduled.max").value(8))
                .andExpect(jsonPath("$.resolvedPools.eventJournalWriters.min").isNumber())
                .andExpect(jsonPath("$.resolvedPools.variableHistoryWriters.max").isNumber());
    }

    private static ClusterProperties cluster() {
        return new ClusterProperties(
                true,
                true,
                30,
                10,
                15,
                10,
                30,
                true,
                2,
                8,
                4,
                6,
                500,
                true,
                true,
                500,
                true,
                "io",
                "",
                "all",
                true,
                2000,
                2,
                true,
                1,
                8,
                50,
                6,
                500,
                1800
        );
    }
}
