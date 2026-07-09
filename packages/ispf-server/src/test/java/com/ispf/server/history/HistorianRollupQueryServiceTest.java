package com.ispf.server.history;

import com.ispf.server.config.AnalyticsProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistorianRollupQueryServiceTest {

    @Mock
    private ClickHouseHistorianRollupStore rollupStore;
    @Mock
    private HistorianRollupSubscriptionIndex subscriptionIndex;

    private final AnalyticsProperties analyticsProperties = new AnalyticsProperties(
            60_000L,
            true,
            true,
            60_000L,
            true,
            60_000L,
            7,
            20,
            3_000L,
            0
    );

    @Test
    void returnsRollupBucketsForLongRangeWhenSubscribed() {
        HistorianRollupQueryService service = new HistorianRollupQueryService(
                rollupStore,
                subscriptionIndex,
                analyticsProperties
        );
        when(rollupStore.isConfigured()).thenReturn(true);
        Instant to = Instant.parse("2026-07-09T00:00:00Z");
        Instant from = to.minus(30, ChronoUnit.DAYS);
        Duration bucket = Duration.ofHours(1);
        List<VariableHistoryService.VariableHistoryBucket> buckets = List.of(
                new VariableHistoryService.VariableHistoryBucket(from, 10.0, 9.0, 11.0, 5)
        );
        when(subscriptionIndex.isSubscribed("root.dev", "temperature", "value", bucket)).thenReturn(true);
        when(rollupStore.queryBuckets("root.dev", "temperature", "value", bucket, from, to, 500))
                .thenReturn(buckets);

        Optional<HistorianRollupQueryService.RollupQueryResult> result = service.tryRollupQuery(
                "root.dev",
                "temperature",
                "value",
                from,
                to,
                bucket,
                500
        );

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().dataSource()).isEqualTo("rollup");
        assertThat(result.orElseThrow().buckets()).hasSize(1);
        verify(rollupStore, never()).materializeRange(any(), any(), any(), anyInt());
    }

    @Test
    void fallsBackWhenRangeTooShort() {
        HistorianRollupQueryService service = new HistorianRollupQueryService(
                rollupStore,
                subscriptionIndex,
                analyticsProperties
        );
        when(rollupStore.isConfigured()).thenReturn(true);
        Instant to = Instant.now();
        Instant from = to.minus(2, ChronoUnit.DAYS);

        Optional<HistorianRollupQueryService.RollupQueryResult> result = service.tryRollupQuery(
                "root.dev",
                "temperature",
                "value",
                from,
                to,
                Duration.ofHours(1),
                100
        );

        assertThat(result).isEmpty();
        verify(rollupStore, never()).queryBuckets(any(), any(), any(), any(), any(), any(), anyInt());
    }
}
