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
class FunctionInvokeApiTest {

    private static final String DEMO_DEVICE = "root.platform.devices.demo-sensor-01";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void invokesFunctionWithoutPayloadBody() throws Exception {
        mockMvc.perform(post("/api/v1/objects/by-path/functions/invoke")
                        .param("path", DEMO_DEVICE)
                        .param("name", "acknowledgeAlarm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].success").value(true));
    }

    @Test
    void invokesFunctionWithPartialPayloadRowsOnly() throws Exception {
        mockMvc.perform(post("/api/v1/objects/by-path/functions/invoke")
                        .param("path", DEMO_DEVICE)
                        .param("name", "acknowledgeAlarm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":[{}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].success").value(true));
    }
}
