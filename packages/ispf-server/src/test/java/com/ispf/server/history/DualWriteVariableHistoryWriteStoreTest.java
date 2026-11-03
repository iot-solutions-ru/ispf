package com.ispf.server.history;

import com.ispf.server.platform.AutomationMetricsRecorder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
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
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DualWriteVariableHistoryWriteStore store = new DualWriteVariableHistoryWriteStore(
                primary,
                secondary,
                new AutomationMetricsRecorder(Optional.of(registry))
        );

        VariableHistoryWriteRecord record = new VariableHistoryWriteRecord(
                "root.platform.devices.demo",
                "temperature",
                "value",
                Instant.parse("2026-07-06T12:00:00Z"),
                Instant.parse("2026-07-06T12:00:00Z"),
                42.0,
                null,
                null
        );

        store.appendOne(record);

        verify(primary).appendOne(record);
        verify(secondary).appendBatch(List.of(record));
        assertEquals(0.0, registry.get("ispf.historian.dual_write.secondary_failures").counter().count());
    }

    @Test
    void secondaryFailureIsVisibleAndRetryDoesNotRewritePrimary() {
        JdbcVariableHistoryWriteStore primary = mock(JdbcVariableHistoryWriteStore.class);
        ClickHouseVariableHistorySecondaryWriteStore secondary =
                mock(ClickHouseVariableHistorySecondaryWriteStore.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DualWriteVariableHistoryWriteStore store = new DualWriteVariableHistoryWriteStore(
                primary,
                secondary,
                new AutomationMetricsRecorder(Optional.of(registry))
        );

        VariableHistoryWriteRecord record = new VariableHistoryWriteRecord(
                "root.platform.devices.demo",
                "temperature",
                "value",
                Instant.parse("2026-07-06T12:00:00Z"),
                Instant.parse("2026-07-06T12:00:00Z"),
                42.0,
                null,
                "GOOD"
        );
        doThrow(new IllegalStateException("CH down")).when(secondary).appendBatch(List.of(record));

        assertThatThrownBy(() -> store.appendOne(record))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("variable history secondary write failed; secondary store is behind primary");
        assertThatThrownBy(() -> store.appendOne(record))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("variable history secondary write failed; secondary store is behind primary");

        verify(primary, times(1)).appendOne(record);
        assertEquals(2.0, registry.get("ispf.historian.dual_write.secondary_failures").counter().count());
        assertEquals(1, store.aheadSize());
    }

    @Test
    void failedSecondaryWritesDoNotGrowAheadSetPastCap() {
        JdbcVariableHistoryWriteStore primary = mock(JdbcVariableHistoryWriteStore.class);
        ClickHouseVariableHistorySecondaryWriteStore secondary =
                mock(ClickHouseVariableHistorySecondaryWriteStore.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DualWriteVariableHistoryWriteStore store = new DualWriteVariableHistoryWriteStore(
                primary,
                secondary,
                new AutomationMetricsRecorder(Optional.of(registry)),
                3
        );
        doThrow(new IllegalStateException("CH down")).when(secondary).appendBatch(anyList());

        for (int i = 0; i < 8; i++) {
            VariableHistoryWriteRecord record = new VariableHistoryWriteRecord(
                    "root.platform.devices.demo",
                    "temperature",
                    "value",
                    Instant.parse("2026-07-06T12:00:00Z").plusSeconds(i),
                    Instant.parse("2026-07-06T12:00:00Z").plusSeconds(i),
                    42.0 + i,
                    null,
                    "GOOD"
            );
            try {
                store.appendOne(record);
            } catch (IllegalStateException ignored) {
                // secondary outage is the case under test
            }
        }

        assertThat(store.aheadSize()).isLessThanOrEqualTo(3);
    }
}
