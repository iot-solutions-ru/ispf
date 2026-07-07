package com.ispf.server.history;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class DualWriteVariableHistoryWriteStoreTest {

    @Test
    void primaryAlwaysWrittenSecondaryBestEffort() {
        JdbcVariableHistoryWriteStore primary = mock(JdbcVariableHistoryWriteStore.class);
        ClickHouseVariableHistorySecondaryWriteStore secondary =
                mock(ClickHouseVariableHistorySecondaryWriteStore.class);
        DualWriteVariableHistoryWriteStore store = new DualWriteVariableHistoryWriteStore(primary, secondary);

        VariableHistoryWriteRecord record = new VariableHistoryWriteRecord(
                "root.platform.devices.demo",
                "temperature",
                "value",
                Instant.parse("2026-07-06T12:00:00Z"),
                Instant.parse("2026-07-06T12:00:00Z"),
                42.0,
                null
        );

        store.appendOne(record);

        verify(primary).appendOne(record);
        verify(secondary).appendBatch(List.of(record));
    }

    @Test
    void secondaryFailureDoesNotPropagate() {
        JdbcVariableHistoryWriteStore primary = mock(JdbcVariableHistoryWriteStore.class);
        ClickHouseVariableHistorySecondaryWriteStore secondary =
                mock(ClickHouseVariableHistorySecondaryWriteStore.class);
        DualWriteVariableHistoryWriteStore store = new DualWriteVariableHistoryWriteStore(primary, secondary);

        VariableHistoryWriteRecord record = new VariableHistoryWriteRecord(
                "root.platform.devices.demo",
                "temperature",
                "value",
                Instant.parse("2026-07-06T12:00:00Z"),
                Instant.parse("2026-07-06T12:00:00Z"),
                42.0,
                null
        );
        doThrow(new IllegalStateException("CH down")).when(secondary).appendBatch(List.of(record));

        store.appendOne(record);

        verify(primary).appendOne(record);
        verifyNoMoreInteractions(primary);
    }
}
