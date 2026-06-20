package com.ispf.server.history;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VariableHistoryApiTest {

    private static final String DEVICE = "root.platform.devices.demo-sensor-01";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private VariableHistoryService variableHistoryService;

    @Test
    void recordsTelemetryAndReturnsHistory() throws Exception {
        double reading = 23.5 + (System.nanoTime() % 100) / 100.0;
        objectManager.setVariableValue(
                DEVICE,
                "temperature",
                DataRecord.single(
                        DataSchema.builder("temperature").field("value", FieldType.DOUBLE).field("unit", FieldType.STRING).build(),
                        Map.of("value", reading, "unit", "C")
                )
        );
        variableHistoryService.recordVariableUpdate(DEVICE, "temperature");

        var response = variableHistoryService.query(DEVICE, "temperature", "value", null, null, 50);
        assertThat(response.samples()).isNotEmpty();
        assertThat(response.samples().stream().anyMatch(sample -> reading == sample.value())).isTrue();

        mockMvc.perform(get("/api/v1/objects/by-path/variables/history")
                        .param("path", DEVICE)
                        .param("name", "temperature")
                        .param("field", "value")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectPath").value(DEVICE))
                .andExpect(jsonPath("$.variableName").value("temperature"))
                .andExpect(jsonPath("$.samples[0].value").exists());
    }

    @Test
    void skipsExcludedDriverVariables() {
        variableHistoryService.recordVariableUpdate(DEVICE, "driverStatus");
        var response = variableHistoryService.query(DEVICE, "driverStatus", "value", null, null, 10);
        assertThat(response.samples()).isEmpty();
    }

    @Test
    void skipsVariablesWithoutHistoryEnabled() {
        variableHistoryService.recordVariableUpdate(DEVICE, "threshold");
        var response = variableHistoryService.query(DEVICE, "threshold", "value", null, null, 10);
        assertThat(response.samples()).isEmpty();
    }

    @Test
    void exportsHistoryAsCsvAndJson() throws Exception {
        double reading = 42.0 + (System.nanoTime() % 50) / 100.0;
        objectManager.setVariableValue(
                DEVICE,
                "temperature",
                DataRecord.single(
                        DataSchema.builder("temperature").field("value", FieldType.DOUBLE).field("unit", FieldType.STRING).build(),
                        Map.of("value", reading, "unit", "C")
                )
        );
        variableHistoryService.recordVariableUpdate(DEVICE, "temperature");

        mockMvc.perform(get("/api/v1/objects/by-path/variables/history/export")
                        .param("path", DEVICE)
                        .param("name", "temperature")
                        .param("field", "value")
                        .param("format", "csv")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("temperature-value.csv")))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(containsString("timestamp,field,value")));

        mockMvc.perform(get("/api/v1/objects/by-path/variables/history/export")
                        .param("path", DEVICE)
                        .param("name", "temperature")
                        .param("field", "value")
                        .param("format", "json")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variableName").value("temperature"))
                .andExpect(jsonPath("$.samples[0].value").exists());
    }

    @Test
    void rejectsUnknownExportFormat() throws Exception {
        mockMvc.perform(get("/api/v1/objects/by-path/variables/history/export")
                        .param("path", DEVICE)
                        .param("name", "temperature")
                        .param("field", "value")
                        .param("format", "xml"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aggregatesHistoryIntoBuckets() throws Exception {
        double base = 10.0 + (System.nanoTime() % 20);
        for (int i = 0; i < 5; i++) {
            objectManager.setVariableValue(
                    DEVICE,
                    "temperature",
                    DataRecord.single(
                            DataSchema.builder("temperature").field("value", FieldType.DOUBLE).build(),
                            Map.of("value", base + i)
                    )
            );
            variableHistoryService.recordVariableUpdate(DEVICE, "temperature");
        }

        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now();
        var response = variableHistoryService.aggregate(DEVICE, "temperature", "value", from, to, "1h", 100);

        assertThat(response.buckets()).isNotEmpty();
        assertThat(response.buckets().getFirst().count()).isPositive();
        assertThat(response.buckets().getFirst().avg()).isNotNull();

        mockMvc.perform(get("/api/v1/objects/by-path/variables/history/aggregate")
                        .param("path", DEVICE)
                        .param("name", "temperature")
                        .param("field", "value")
                        .param("bucket", "1h")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucket").value("1h"))
                .andExpect(jsonPath("$.buckets[0].avg").exists())
                .andExpect(jsonPath("$.buckets[0].min").exists())
                .andExpect(jsonPath("$.buckets[0].max").exists());
    }

    @Test
    void rejectsUnknownAggregateBucket() throws Exception {
        mockMvc.perform(get("/api/v1/objects/by-path/variables/history/aggregate")
                        .param("path", DEVICE)
                        .param("name", "temperature")
                        .param("field", "value")
                        .param("bucket", "2w"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void queryReturnsEmptyWhenHistoryDisabled() throws Exception {
        mockMvc.perform(get("/api/v1/objects/by-path/variables/history")
                        .param("path", DEVICE)
                        .param("name", "threshold")
                        .param("field", "value")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.samples").isEmpty());
    }
}
