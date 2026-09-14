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
class FunctionInvokeBatchApiTest {

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
    void invokeBatchAcknowledgesMultipleTargetsInOneRequest() throws Exception {
        mockMvc.perform(post("/api/v1/objects/by-path/functions/invoke-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    { "path": "%s", "name": "acknowledgeAlarm" },
                                    { "path": "%s", "name": "acknowledgeAlarm" },
                                    { "path": "root.platform.devices.missing-device", "name": "acknowledgeAlarm" }
                                  ]
                                }
                                """.formatted(DEMO_DEVICE, DEMO_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(3))
                .andExpect(jsonPath("$.results[0].ok").value(true))
                .andExpect(jsonPath("$.results[1].ok").value(true))
                .andExpect(jsonPath("$.results[2].ok").value(false));
    }

    @Test
    void invokeBatchRejectsOversizedPayload() throws Exception {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < FunctionController.MAX_BATCH_SIZE + 1; i++) {
            if (i > 0) {
                items.append(',');
            }
            items.append("{\"path\":\"").append(DEMO_DEVICE).append("\",\"name\":\"acknowledgeAlarm\"}");
        }
        mockMvc.perform(post("/api/v1/objects/by-path/functions/invoke-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[" + items + "]}"))
                .andExpect(status().isBadRequest());
    }
}
