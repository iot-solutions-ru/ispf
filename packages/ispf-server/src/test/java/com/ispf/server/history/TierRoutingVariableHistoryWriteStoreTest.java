package com.ispf.server.history;

import com.ispf.server.config.HistorianTierProfile;
import com.ispf.server.config.HistorianTierProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TierRoutingVariableHistoryWriteStoreTest {

    private HistorianTierProperties tierProperties;
    private JdbcVariableHistoryWriteStore hotStore;
    private ClickHouseWarmTierWriteStore warmStore;
    private TierRoutingVariableHistoryWriteStore router;

    @BeforeEach
    void setUp() {
        tierProperties = new HistorianTierProperties();
        HistorianTierProfile hot = tierProperties.hotTier();
        hot.setRetentionDays(7);
        hot.setDualWriteEnabled(false);
        HistorianTierProfile warm = tierProperties.warmTier();
        warm.setEnabled(true);
        warm.setStore("clickhouse");

        hotStore = mock(JdbcVariableHistoryWriteStore.class);
        warmStore = mock(ClickHouseWarmTierWriteStore.class);
        when(warmStore.isConfigured()).thenReturn(true);
        router = new TierRoutingVariableHistoryWriteStore(tierProperties, hotStore, warmStore);
    }

    @Test
    void routesRecentSamplesToHotStoreOnly() {
        Instant now = Instant.now();
        VariableHistoryWriteRecord recent = record(now.minus(1, ChronoUnit.HOURS), now);

        router.appendBatch(List.of(recent));

        verify(hotStore).appendBatch(List.of(recent));
        verify(warmStore, never()).appendBatch(anyList());
    }

    @Test
    void routesAgedSamplesToWarmStoreOnly() {
        Instant now = Instant.now();
        Instant observed = now.minus(10, ChronoUnit.DAYS);
        VariableHistoryWriteRecord aged = record(observed, now);

        router.appendBatch(List.of(aged));

        verify(warmStore).appendBatch(List.of(aged));
        verify(hotStore, never()).appendBatch(anyList());
    }

    @Test
    void splitsMixedBatchAcrossTiers() {
        Instant now = Instant.now();
        VariableHistoryWriteRecord recent = record(now.minus(2, ChronoUnit.HOURS), now);
        VariableHistoryWriteRecord aged = record(now.minus(14, ChronoUnit.DAYS), now);

        router.appendBatch(List.of(recent, aged));

        verify(hotStore).appendBatch(List.of(recent));
        verify(warmStore).appendBatch(List.of(aged));
    }

    @Test
    void dualWritesRecentHotSamplesWhenEnabled() {
        tierProperties.hotTier().setDualWriteEnabled(true);
        Instant now = Instant.now();
        VariableHistoryWriteRecord recent = record(now.minus(1, ChronoUnit.HOURS), now);

        router.appendBatch(List.of(recent));

        verify(hotStore).appendBatch(List.of(recent));
        verify(warmStore).appendBestEffort(List.of(recent));
    }

    @Test
    void warmAppendFailureIsVisibleAndRetryDoesNotRewriteHot() {
        tierProperties.hotTier().setDualWriteEnabled(true);
        Instant now = Instant.now();
        VariableHistoryWriteRecord recent = record(now.minus(1, ChronoUnit.HOURS), now);
        doThrow(new IllegalStateException("warm down"))
                .when(warmStore)
                .appendBestEffort(List.of(recent));

        assertThatThrownBy(() -> router.appendBatch(List.of(recent)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("warm-tier append failed; warm store is behind hot");
        assertThatThrownBy(() -> router.appendBatch(List.of(recent)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("warm-tier append failed; warm store is behind hot");

        verify(hotStore, times(1)).appendBatch(List.of(recent));
        assertThat(router.aheadSize()).isEqualTo(1);
    }

    @Test
    void failedWarmWritesDoNotGrowAheadSetPastCap() {
        tierProperties.hotTier().setDualWriteEnabled(true);
        router = new TierRoutingVariableHistoryWriteStore(tierProperties, hotStore, warmStore, 3);
        doThrow(new IllegalStateException("warm down")).when(warmStore).appendBestEffort(anyList());

        Instant now = Instant.now();
        for (int i = 0; i < 8; i++) {
            VariableHistoryWriteRecord recent = record(now.minus(1, ChronoUnit.HOURS).plusSeconds(i), now.plusSeconds(i));
            try {
                router.appendBatch(List.of(recent));
            } catch (IllegalStateException ignored) {
                // warm outage is the case under test
            }
        }

        assertThat(router.aheadSize()).isLessThanOrEqualTo(3);
    }

    @Test
    void classifiesByObservedAtNotIngestTime() {
        Instant hotCutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant observed = hotCutoff.minus(1, ChronoUnit.HOURS);
        VariableHistoryWriteRecord aged = record(observed, Instant.now());

        assertThat(TierRoutingVariableHistoryWriteStore.isWarmTierSample(aged, hotCutoff)).isTrue();
    }

    private static VariableHistoryWriteRecord record(Instant observedAt, Instant sampledAt) {
        return new VariableHistoryWriteRecord(
                "root.platform.devices.demo",
                "temperature",
                "value",
                sampledAt,
                observedAt,
                42.0,
                null,
                null
        );
    }
}
