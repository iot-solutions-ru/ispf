package com.ispf.server.plugin.blueprint;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "ispf.bootstrap.fixtures-enabled=true")
class BlueprintControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsFixtureMqttSensorModelWhenFixturesEnabled() throws Exception {
        mockMvc.perform(get("/api/v1/blueprints"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='mqtt-sensor-v1')]").exists())
                .andExpect(jsonPath("$[?(@.name=='mqtt-sensor-v1')].type").value("MIXIN"));
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
