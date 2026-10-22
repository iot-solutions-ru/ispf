package com.ispf.server.history;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.server.config.VariableHistoryProperties;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "ispf.variable-history.min-interval-ms=5000"
})
@ActiveProfiles("test")
class VariableHistoryDebounceChangesOnlyTest {

    private static final String PATH = "root.platform.devices.history-debounce-probe";

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private VariableHistoryService variableHistoryService;

    @Autowired
    private VariableHistoryProperties historyProperties;

    @AfterEach
    void cleanup() {
        try {
            objectManager.delete(PATH);
        } catch (RuntimeException ignored) {
            // best effort
        }
    }

    @Test
    void debouncedValueIsHistorizedAfterTheInterval() {
        assertThat(historyProperties.getMinIntervalMs()).isEqualTo(5000);

        objectManager.create(
                "root.platform.devices",
                "history-debounce-probe",
                ObjectType.DEVICE,
                "history debounce",
                null,
                null
        );
        DataSchema schema = DataSchema.builder("temperature").field("value", FieldType.DOUBLE).build();
        objectManager.createVariable(
                PATH,
                "temperature",
                schema,
                true,
                true,
                DataRecord.single(schema, Map.of("value", 10.0)),
                true,
                null
        );

        // Relative to wall clock so debounce keys are not behind any earlier samples.
        Instant t0 = Instant.now();
        Instant t1 = t0.plusSeconds(1);
        Instant t2 = t0.plusSeconds(6);

        DataRecord valueA = DataRecord.single(schema, Map.of("value", 10.0));
        DataRecord valueB = DataRecord.single(schema, Map.of("value", 20.0));

        clearHistorianCaches();

        variableHistoryService.recordFromDataRecordTrusted(PATH, "temperature", valueA, t0);
        assertThat(historizedValue()).isEqualTo(10.0);

        variableHistoryService.recordFromDataRecordTrusted(PATH, "temperature", valueB, t1);
        assertThat(historizedValue())
                .as("debounced B must not update last historized record")
                .isEqualTo(10.0);

        variableHistoryService.recordFromDataRecordTrusted(PATH, "temperature", valueB, t2);
        assertThat(historizedValue()).isEqualTo(20.0);

        var history = variableHistoryService.query(PATH, "temperature", "value", null, null, 50);
        assertThat(history.samples().stream().map(VariableHistoryService.VariableHistorySample::value).toList())
                .as("history values after A, dropped B, then B")
                .contains(10.0, 20.0);
    }

    private Double historizedValue() {
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, DataRecord> cache = (ConcurrentHashMap<String, DataRecord>) ReflectionTestUtils
                .getField(variableHistoryService, "lastHistorizedRecord");
        DataRecord record = cache.get(PATH + "|temperature");
        if (record == null) {
            return null;
        }
        return ((Number) record.firstRow().get("value")).doubleValue();
    }

    private void clearHistorianCaches() {
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, DataRecord> historized = (ConcurrentHashMap<String, DataRecord>) ReflectionTestUtils
                .getField(variableHistoryService, "lastHistorizedRecord");
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Long> debounce = (ConcurrentHashMap<String, Long>) ReflectionTestUtils
                .getField(variableHistoryService, "lastSampleMs");
        historized.clear();
        debounce.clear();
    }
}
