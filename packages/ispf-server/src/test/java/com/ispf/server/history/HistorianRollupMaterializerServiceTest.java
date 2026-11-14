package com.ispf.server.history;

import com.ispf.server.config.AnalyticsProperties;
import com.ispf.server.platform.analytics.AnalyticsClusterWorkloadService;
import com.ispf.server.security.acl.VariableMemberAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistorianRollupMaterializerServiceTest {

    @Mock
    private ClickHouseHistorianRollupStore rollupStore;
    @Mock
    private HistorianRollupSubscriptionIndex subscriptionIndex;
    @Mock
    private AnalyticsClusterWorkloadService analyticsClusterWorkloadService;
    @Mock
    private VariableMemberAccessService variableMemberAccessService;

    @Test
    void lateSampleRewritesTheDirtyDayBucketOnTheNextTick() {
        AnalyticsProperties analytics = new AnalyticsProperties(
                60_000L, true, true, 60_000L, true, 60_000L, 7, 20, 3000L, 600
        );
        when(rollupStore.isConfigured()).thenReturn(true);
        when(analyticsClusterWorkloadService.isAnalyticsWorkloadActive()).thenReturn(true);
        HistorianRollupSubscription day = new HistorianRollupSubscription(
                "root.platform.devices.line",
                "temperature",
                "value",
                Duration.ofDays(1)
        );
        when(subscriptionIndex.listSubscriptions()).thenReturn(List.of(day));
        when(rollupStore.maxMaterializedBucketStart(any(), any(), any(), any()))
                .thenReturn(Optional.of(Instant.now()));
        Instant late = Instant.now().minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
        when(rollupStore.materializeRange(eq(day), any(), any(), anyInt())).thenAnswer(invocation -> {
            Instant from = invocation.getArgument(1);
            return from.equals(late) ? 1 : 0;
        });

        HistorianRollupMaterializerService service = new HistorianRollupMaterializerService(
                rollupStore,
                subscriptionIndex,
                analytics,
                analyticsClusterWorkloadService,
                variableMemberAccessService
        );
        service.noteLateSample(day.objectPath(), day.variableName(), day.fieldName(), late);

        HistorianRollupMaterializerService.TickResult tick = service.materializeTick();

        assertTrue(tick.ran());
        assertEquals(1, tick.bucketsWritten());
        verify(rollupStore).materializeRange(
                eq(day),
                eq(late),
                eq(late.plus(Duration.ofDays(1)).minusMillis(1)),
                eq(8)
        );
    }
}
