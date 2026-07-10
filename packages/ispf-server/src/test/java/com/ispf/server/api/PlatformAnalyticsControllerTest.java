package com.ispf.server.api;

import com.ispf.server.config.VariableHistorySloProperties;
import com.ispf.server.history.HistorianQueryMetricsRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformAnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HistorianQueryMetricsRecorder historianQueryMetricsRecorder;

    @Autowired
    private VariableHistorySloProperties sloProperties;

    @Test
    void historianSlaEndpointExposesP50AndP95() throws Exception {
        historianQueryMetricsRecorder.recordAggregateQuery(15);
        historianQueryMetricsRecorder.recordAggregateQuery(25);
        historianQueryMetricsRecorder.recordRawQuery(5);

        mockMvc.perform(get("/api/v1/platform/analytics/historian-sla"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aggregate.p50LatencyMs").isNumber())
                .andExpect(jsonPath("$.aggregate.p95LatencyMs").isNumber())
                .andExpect(jsonPath("$.aggregate.sloMaxLatencyMs").value(sloProperties.getAggregateMaxLatencyMs()))
                .andExpect(jsonPath("$.aggregate.sloMaxPoints").value(sloProperties.getAggregateMaxPoints()))
                .andExpect(jsonPath("$.raw.p50LatencyMs").isNumber());
    }

    @Test
    void analyticsSloEndpointExposesBl210Targets() throws Exception {
        mockMvc.perform(get("/api/v1/platform/analytics/analytics-slo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.multiTagQueryP95LatencyMs").value(3000))
                .andExpect(jsonPath("$.catalogMinTags").value(50000))
                .andExpect(jsonPath("$.clickhouseMinSamples").value(1000000000));
    }
}
