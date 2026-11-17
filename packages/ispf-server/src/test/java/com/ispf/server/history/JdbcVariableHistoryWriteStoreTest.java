package com.ispf.server.history;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.jdbc.core.PreparedStatementSetter;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
                VariableHistoryWriteRecord.ingested(
                        "root.a", "temperature", "value", Instant.parse("2026-01-01T00:00:00Z"), 1.5, null),
                VariableHistoryWriteRecord.ingested(
                        "root.b", "temperature", "value", Instant.parse("2026-01-01T00:00:01Z"), 2.5, null)
        );

        store.appendBatch(records);

        ArgumentCaptor<Integer> batchSizeCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(jdbcTemplate).batchUpdate(
                eq("""
                        INSERT INTO variable_samples (object_path, variable_name, field_name, sampled_at, observed_at, value_double, value_text, quality)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """),
                eq(records),
                batchSizeCaptor.capture(),
                any(ParameterizedPreparedStatementSetter.class)
        );
        assertEquals(2, batchSizeCaptor.getValue());
    }

    @Test
    void appendOneBindsTimestampsInUtc() throws Exception {
        Instant sampled = Instant.parse("2026-11-16T18:18:13.732Z");
        VariableHistoryWriteRecord record = VariableHistoryWriteRecord.ingested(
                "root.a", "temperature", "value", sampled, -19.4, null
        );

        store.appendOne(record);

        ArgumentCaptor<PreparedStatementSetter> captor = ArgumentCaptor.forClass(PreparedStatementSetter.class);
        verify(jdbcTemplate).update(anyString(), captor.capture());

        PreparedStatement statement = mock(PreparedStatement.class);
        captor.getValue().setValues(statement);
        assertUtcTimestamp(statement, 4, sampled);
        assertUtcTimestamp(statement, 5, sampled);
    }

    @Test
    void appendBatchBindsTimestampsInUtc() throws Exception {
        Instant sampled = Instant.parse("2026-11-16T18:18:13.732Z");
        VariableHistoryWriteRecord record = VariableHistoryWriteRecord.ingested(
                "root.a", "temperature", "value", sampled, -19.4, null
        );

        store.appendBatch(List.of(record));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ParameterizedPreparedStatementSetter<VariableHistoryWriteRecord>> captor =
                ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
        verify(jdbcTemplate).batchUpdate(anyString(), eq(List.of(record)), eq(1), captor.capture());

        PreparedStatement statement = mock(PreparedStatement.class);
        captor.getValue().setValues(statement, record);
        assertUtcTimestamp(statement, 4, sampled);
        assertUtcTimestamp(statement, 5, sampled);
    }

    private static void assertUtcTimestamp(PreparedStatement statement, int index, Instant expected) throws Exception {
        ArgumentCaptor<Timestamp> timestamp = ArgumentCaptor.forClass(Timestamp.class);
        ArgumentCaptor<Calendar> calendar = ArgumentCaptor.forClass(Calendar.class);
        verify(statement).setTimestamp(eq(index), timestamp.capture(), calendar.capture());
        assertEquals("UTC", calendar.getValue().getTimeZone().getID());
        assertEquals(expected.toEpochMilli(), timestamp.getValue().getTime());
    }
}
