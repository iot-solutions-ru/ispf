package com.ispf.server.history;

import com.ispf.server.config.HistorianTierProfile;
import com.ispf.server.config.HistorianTierProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TierRoutingVariableHistoryQueryStoreTest {

    private HistorianTierProperties tierProperties;
    private JdbcVariableHistoryQueryStore hotStore;
    private ClickHouseWarmTierQueryStore warmStore;
    private TierRoutingVariableHistoryQueryStore router;

    @BeforeEach
    void setUp() {
        tierProperties = new HistorianTierProperties();
        HistorianTierProfile hot = tierProperties.hotTier();
        hot.setRetentionDays(7);
        HistorianTierProfile warm = tierProperties.warmTier();
        warm.setEnabled(true);
        warm.setStore("clickhouse");
        warm.getClickhouse().setUrl("http://localhost:8123");

        hotStore = mock(JdbcVariableHistoryQueryStore.class);
        warmStore = mock(ClickHouseWarmTierQueryStore.class);
        when(warmStore.isConfigured()).thenReturn(true);
        router = new TierRoutingVariableHistoryQueryStore(tierProperties, hotStore, warmStore);
    }

    @Test
    void routesRecentRangeToHotStoreOnly() {
        Instant now = Instant.now();
        Instant from = now.minus(2, ChronoUnit.DAYS);
        Instant to = now;
        List<VariableHistoryService.VariableHistorySample> expected = List.of(
                new VariableHistoryService.VariableHistorySample(from, 1.0, null)
        );
        when(hotStore.query("path", "temp", "value", from, to, 100)).thenReturn(expected);

        var result = router.query("path", "temp", "value", from, to, 100);

        assertThat(result).isEqualTo(expected);
        verify(warmStore, never()).query(any(), any(), any(), any(), any(), any(Integer.class));
    }

    @Test
    void routesOldRangeToWarmStoreOnly() {
        Instant now = Instant.now();
        Instant from = now.minus(30, ChronoUnit.DAYS);
        Instant to = now.minus(10, ChronoUnit.DAYS);
        List<VariableHistoryService.VariableHistorySample> expected = List.of(
                new VariableHistoryService.VariableHistorySample(from, 2.0, null)
        );
        when(warmStore.query("path", "temp", "value", from, to, 50)).thenReturn(expected);

        var result = router.query("path", "temp", "value", from, to, 50);

        assertThat(result).isEqualTo(expected);
        verify(hotStore, never()).query(any(), any(), any(), any(), any(), any(Integer.class));
    }

    @Test
    void mergesSpanningRangeAcrossHotAndWarm() {
        Instant now = Instant.now();
        Instant from = now.minus(14, ChronoUnit.DAYS);
        Instant to = now;

        when(warmStore.query(eq("path"), eq("temp"), eq("value"), eq(from), any(Instant.class), eq(100)))
                .thenReturn(List.of(new VariableHistoryService.VariableHistorySample(from, 1.0, null)));
        when(hotStore.query(eq("path"), eq("temp"), eq("value"), any(Instant.class), eq(to), eq(99)))
                .thenReturn(List.of(new VariableHistoryService.VariableHistorySample(to, 2.0, null)));

        var result = router.query("path", "temp", "value", from, to, 100);

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().value()).isEqualTo(1.0);
        assertThat(result.get(1).value()).isEqualTo(2.0);
    }

    @Test
    void routesAggregateOlderThanHotRetentionToWarm() {
        Instant now = Instant.now();
        Instant from = now.minus(30, ChronoUnit.DAYS);
        Instant to = now.minus(10, ChronoUnit.DAYS);
        Duration bucket = Duration.ofHours(1);
        List<VariableHistoryService.VariableHistoryBucket> expected = List.of(
                new VariableHistoryService.VariableHistoryBucket(from, 3.0, 2.0, 4.0, 5)
        );
        when(warmStore.aggregateBuckets("path", "temp", "value", from, to, bucket, 200)).thenReturn(expected);

        var result = router.aggregateBuckets("path", "temp", "value", from, to, bucket, 200);

        assertThat(result).isEqualTo(expected);
        verify(hotStore, never()).aggregateBuckets(any(), any(), any(), any(), any(), any(), any(Integer.class));
    }
}
