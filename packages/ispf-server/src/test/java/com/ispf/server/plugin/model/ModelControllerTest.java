package com.ispf.server.plugin.model;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ModelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsBuiltInMqttSensorModel() throws Exception {
        mockMvc.perform(get("/api/v1/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='mqtt-sensor-v1')]").exists())
                .andExpect(jsonPath("$[?(@.name=='mqtt-sensor-v1')].type").value("RELATIVE"));
    }

    @Test
    void demoDeviceHasModelVariablesAfterApply() throws Exception {
        mockMvc.perform(get("/api/v1/objects/by-path/variables")
                        .param("path", "root.platform.devices.demo-sensor-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='temperature')]").exists())
                .andExpect(jsonPath("$[?(@.name=='threshold')]").exists());
    }
}
