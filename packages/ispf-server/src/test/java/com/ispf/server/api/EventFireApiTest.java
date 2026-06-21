package com.ispf.server.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventFireApiTest {

    private static final String DEMO_DEVICE = "root.platform.devices.demo-sensor-01";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void firesEventWithoutPayloadBody() throws Exception {
        mockMvc.perform(post("/api/v1/events/fire")
                        .param("objectPath", DEMO_DEVICE)
                        .param("eventName", "thresholdExceeded"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventName").value("thresholdExceeded"));
    }

    @Test
    void firesEventWithPartialPayloadRowsOnly() throws Exception {
        mockMvc.perform(post("/api/v1/events/fire")
                        .param("objectPath", DEMO_DEVICE)
                        .param("eventName", "thresholdExceeded")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":[{}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventName").value("thresholdExceeded"));
    }
}
