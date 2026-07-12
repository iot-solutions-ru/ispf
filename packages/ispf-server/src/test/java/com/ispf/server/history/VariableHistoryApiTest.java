package com.ispf.server.history;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
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
    void skipsDuplicateSamplesWhenChangesOnlyMode() {
        DataRecord record = DataRecord.single(
                DataSchema.builder("temperature").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 42.0)
        );
        var before = variableHistoryService.query(DEVICE, "temperature", "value", null, null, 50);
        int countBefore = before.samples().size();

        variableHistoryService.recordFromDataRecordTrusted(DEVICE, "temperature", record, Instant.now());
        var afterFirst = variableHistoryService.query(DEVICE, "temperature", "value", null, null, 50);
        assertThat(afterFirst.samples()).hasSize(countBefore + 1);

        variableHistoryService.recordFromDataRecordTrusted(DEVICE, "temperature", record, Instant.now());
        var afterDuplicate = variableHistoryService.query(DEVICE, "temperature", "value", null, null, 50);
        assertThat(afterDuplicate.samples()).hasSize(countBefore + 1);
    }

    @Test
    void recordsFullVariableSnapshot() {
        DataRecord record = DataRecord.single(
                DataSchema.builder("temperature").field("value", FieldType.DOUBLE).field("unit", FieldType.STRING).build(),
                Map.of("value", 17.5, "unit", "C")
        );
        variableHistoryService.recordFromDataRecordTrusted(DEVICE, "temperature", record, Instant.now());

        var snapshot = variableHistoryService.query(
                DEVICE,
                "temperature",
                VariableHistoryService.RECORD_SNAPSHOT_FIELD,
                null,
                null,
                10
        );
        assertThat(snapshot.samples()).isNotEmpty();
        String json = snapshot.samples().getLast().text();
        assertThat(json).contains("\"unit\"");
        assertThat(json).contains("C");
        assertThat(json).contains("17.5");
        assertThat(json).contains("\"rows\"");
        assertThat(json).contains("\"schema\"");
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

        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now();

        mockMvc.perform(get("/api/v1/objects/by-path/variables/history/export")
                        .param("path", DEVICE)
                        .param("name", "temperature")
                        .param("field", "value")
                        .param("format", "csv")
                        .param("from", from.toString())
                        .param("to", to.toString())
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
    void parquetExportReturnsParquetBinary() throws Exception {
        double reading = 12.5;
        variableHistoryService.recordObservedSample(DEVICE, "temperature", "value", reading, Instant.now());

        byte[] body = mockMvc.perform(get("/api/v1/objects/by-path/variables/history/export")
                        .param("path", DEVICE)
                        .param("name", "temperature")
                        .param("field", "value")
                        .param("format", "parquet")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("temperature-value.parquet")))
                .andExpect(header().string("X-ISPF-Export-Format", "parquet"))
                .andExpect(content().contentTypeCompatibleWith("application/vnd.apache.parquet"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(body.length).isGreaterThan(8);
        assertThat(new String(body, 0, 4)).isEqualTo("PAR1");
        assertThat(new String(body, body.length - 4, 4)).isEqualTo("PAR1");
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

    @Test
    void observedAtUsedAsChartTimestamp() {
        Instant observed = Instant.now().minusSeconds(7200).truncatedTo(ChronoUnit.MILLIS);
        double reading = 17.3;
        variableHistoryService.recordObservedSample(DEVICE, "temperature", "value", reading, observed);

        var response = variableHistoryService.query(DEVICE, "temperature", "value", null, null, 50);
        assertThat(response.samples()).isNotEmpty();
        var sample = response.samples().stream()
                .filter(item -> reading == item.value())
                .findFirst()
                .orElseThrow();
        assertThat(sample.ts()).isEqualTo(observed);
        assertThat(sample.ingestedAt()).isAfter(observed);
    }

    @Test
    void calendarRangeTodayReturnsSamples() throws Exception {
        double reading = 55.0 + (System.nanoTime() % 10) / 10.0;
        variableHistoryService.recordObservedSample(DEVICE, "temperature", "value", reading, Instant.now());

        mockMvc.perform(get("/api/v1/objects/by-path/variables/history")
                        .param("path", DEVICE)
                        .param("name", "temperature")
                        .param("field", "value")
                        .param("calendarRange", "today")
                        .param("timeZone", "UTC")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.samples[0].value").exists());
    }
}
