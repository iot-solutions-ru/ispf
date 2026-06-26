package com.ispf.server.history;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JdbcVariableHistoryWriteStoreTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private JdbcVariableHistoryWriteStore store;

    @BeforeEach
    void setUp() {
        store = new JdbcVariableHistoryWriteStore(jdbcTemplate);
    }

    @Test
    void appendBatchUsesJdbcBatchUpdate() {
        List<VariableHistoryWriteRecord> records = List.of(
                new VariableHistoryWriteRecord("root.a", "temperature", "value", Instant.parse("2026-01-01T00:00:00Z"), 1.5, null),
                new VariableHistoryWriteRecord("root.b", "temperature", "value", Instant.parse("2026-01-01T00:00:01Z"), 2.5, null)
        );

        store.appendBatch(records);

        ArgumentCaptor<Integer> batchSizeCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(jdbcTemplate).batchUpdate(
                eq("""
                        INSERT INTO variable_samples (object_path, variable_name, field_name, sampled_at, value_double, value_text)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """),
                eq(records),
                batchSizeCaptor.capture(),
                any(ParameterizedPreparedStatementSetter.class)
        );
        assertEquals(2, batchSizeCaptor.getValue());
    }

    @Test
    void appendOneUsesSingleUpdate() {
        VariableHistoryWriteRecord record = new VariableHistoryWriteRecord(
                "root.a", "pressure", "value", Instant.parse("2026-01-01T00:00:00Z"), 3.0, "bar"
        );

        store.appendOne(record);

        verify(jdbcTemplate).update(
                eq("""
                        INSERT INTO variable_samples (object_path, variable_name, field_name, sampled_at, value_double, value_text)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """),
                eq("root.a"),
                eq("pressure"),
                eq("value"),
                any(),
                eq(3.0),
                eq("bar")
        );
    }
}
