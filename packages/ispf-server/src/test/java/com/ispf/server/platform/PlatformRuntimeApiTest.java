package com.ispf.server.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformRuntimeApiTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void listsFunctionInvocationAudit() throws Exception {
        mockMvc.perform(post("/api/v1/objects/by-path/functions/invoke")
                        .param("path", "root.platform.devices.demo-sensor-01")
                        .param("name", "acknowledgeAlarm")
                        .header("X-ISPF-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/platform/function-invocations")
                        .param("objectPath", "root.platform.devices.demo-sensor-01")
                        .param("functionName", "acknowledgeAlarm")
                        .header("X-ISPF-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].objectPath").value("root.platform.devices.demo-sensor-01"))
                .andExpect(jsonPath("$[0].functionName").value("acknowledgeAlarm"))
                .andExpect(jsonPath("$[0].success").value(true));
    }
}
