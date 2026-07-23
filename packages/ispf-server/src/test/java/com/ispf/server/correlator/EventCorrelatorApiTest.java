package com.ispf.server.correlator;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.driver.DriverRuntimeService;
import com.ispf.server.object.BindingStateVariables;
import com.ispf.server.object.ObjectBindingStatePort;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class EventCorrelatorApiTest {

    private static final String DEMO_DEVICE = "root.platform.devices.demo-sensor-01";

    private static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();
    private static final DataSchema TEMPERATURE = DataSchema.builder("temperature")
            .field("value", FieldType.DOUBLE)
            .field("unit", FieldType.STRING)
            .build();
    private static final DataSchema THRESHOLD = DataSchema.builder("threshold")
            .field("value", FieldType.DOUBLE)
            .build();
    private static final DataSchema ALARM_ACK = DataSchema.builder("alarmAcknowledged")
            .field("value", FieldType.BOOLEAN)
            .build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private ObjectBindingStatePort bindingStatePort;

    @Autowired
    private DriverRuntimeService driverRuntimeService;

    @BeforeEach
    void resetDemoSensorBindingState() {
        driverRuntimeService.stop(DEMO_DEVICE);
        bindingStatePort.invalidateCache(DEMO_DEVICE);
        if (objectManager.tree().findByPath(DEMO_DEVICE).isPresent()) {
            objectManager.upsertSystemVariable(
                    DEMO_DEVICE,
                    BindingStateVariables.BINDING_STATE,
                    STRING_VALUE,
                    DataRecord.single(STRING_VALUE, Map.of("value", "{}"))
            );
            bindingStatePort.invalidateCache(DEMO_DEVICE);
        }
    }

    @Test
    void listsSeededDemoCorrelator() throws Exception {
        mockMvc.perform(get("/api/v1/correlators"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].eventName", hasItem("thresholdExceeded")));
    }

    @Test
    void rejectsWebhookActionTargetPointingToCloudMetadata() throws Exception {
        mockMvc.perform(post("/api/v1/correlators")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "SSRF webhook test correlator",
                                  "patternType": "COUNT",
                                  "eventName": "ssrfWebhookTestEvent",
                                  "windowSeconds": 0,
                                  "minOccurrences": 1,
                                  "cooldownSeconds": 0,
                                  "sequenceGapSeconds": 0,
                                  "actionType": "SEND_WEBHOOK",
                                  "actionTarget": "http://169.254.169.254/latest/meta-data",
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("169.254.169.254")));
    }

    @Test
    void acceptsWebhookActionTargetOnInternalHost() throws Exception {
        String suffix = Long.toHexString(System.nanoTime()).substring(0, 6);
        String name = "SSRF webhook ok correlator " + suffix;
        mockMvc.perform(post("/api/v1/correlators")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "patternType": "COUNT",
                                  "eventName": "ssrfWebhookOkEvent%s",
                                  "windowSeconds": 0,
                                  "minOccurrences": 1,
                                  "cooldownSeconds": 0,
                                  "sequenceGapSeconds": 0,
                                  "actionType": "SEND_WEBHOOK",
                                  "actionTarget": "http://127.0.0.1:9/hook",
                                  "enabled": false
                                }
                                """.formatted(name, suffix)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/correlators/by-path")
                        .param("path", AutomationTreeService.correlatorPathForName(name)))
                .andExpect(status().isOk());
    }

    @Test
    void correlatorStartsWorkflowOnThresholdEvent() throws Exception {
        driverRuntimeService.stop(DEMO_DEVICE);

        objectManager.setVariableValue(
                DEMO_DEVICE,
                "alarmAcknowledged",
                DataRecord.single(ALARM_ACK, Map.of("value", false))
        );
        objectManager.setVariableValue(
                DEMO_DEVICE,
                "temperature",
                DataRecord.single(TEMPERATURE, Map.of("value", 20.0, "unit", "C"))
        );
        objectManager.setVariableValue(
                DEMO_DEVICE,
                "threshold",
                DataRecord.single(THRESHOLD, Map.of("value", 80.0))
        );
        objectManager.setVariableValue(
                DEMO_DEVICE,
                "temperature",
                DataRecord.single(TEMPERATURE, Map.of("value", 95.0, "unit", "C"))
        );

        awaitWorkQueueTitle("Подтвердите тревогу");
    }

    private void awaitWorkQueueTitle(String expectedTitle) throws Exception {
        long deadline = System.nanoTime() + 30_000_000_000L;
        while (System.nanoTime() < deadline) {
            MvcResult result = mockMvc.perform(get("/api/v1/work-queue"))
                    .andExpect(status().isOk())
                    .andReturn();
            String body = result.getResponse().getContentAsString();
            if (body.contains(expectedTitle)) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Work queue did not contain task: " + expectedTitle);
    }
}
