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
class ObjectEditorApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void editorEndpointReturnsVariablesAndEvents() throws Exception {
        mockMvc.perform(get("/api/v1/objects/by-path/editor")
                        .param("path", "root.platform.devices.demo-sensor-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.object.path").value("root.platform.devices.demo-sensor-01"))
                .andExpect(jsonPath("$.variables[?(@.name=='temperature')]").exists())
                .andExpect(jsonPath("$.events[?(@.name=='thresholdExceeded')]").exists());
    }
}
