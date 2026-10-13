package com.ispf.server.history;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
    void secondaryFailureIsVisibleAndRetryDoesNotRewritePrimary() {
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

        assertThatThrownBy(() -> store.appendOne(record))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("variable history secondary write failed; secondary store is behind primary");
        assertThatThrownBy(() -> store.appendOne(record))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("variable history secondary write failed; secondary store is behind primary");

        verify(primary, times(1)).appendOne(record);
    }
}
