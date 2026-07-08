package com.ispf.server.api;

import com.ispf.server.bootstrap.DemoFixtureBootstrap;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.ObjectTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Isolated
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class FunctionInvokeApiTest {

    private static final String DEMO_DEVICE = "root.platform.devices.demo-sensor-01";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private ObjectTemplateService objectTemplateService;

    @BeforeEach
    void ensureDemoSensorAcknowledgeAlarmFunction() {
        var node = objectManager.tree().findByPath(DEMO_DEVICE);
        if (node.isEmpty() || !node.get().functions().containsKey("acknowledgeAlarm")) {
            objectTemplateService.applyTemplate(DEMO_DEVICE, DemoFixtureBootstrap.MQTT_SENSOR_MODEL);
        }
    }

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
