package com.ispf.server.platform.analytics.engine;

import com.ispf.analytics.engine.AnalyticsSourceRef;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.server.config.AnalyticsProperties;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.PlatformLeaderLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsEngineSchedulerFailureTest {

    private static final String TAG_PATH = "root.platform.devices.sensor-a#rolling-avg";
    private static final long PERIODIC_MS = 60_000L;

    @Mock
    private AnalyticsEngineService engineService;
    @Mock
    private AnalyticsTagCatalogService catalogService;
    @Mock
    private AnalyticsScheduleRegistry scheduleRegistry;
    @Mock
    private PlatformLeaderLockService leaderLockService;
    @Mock
    private ClusterProperties clusterProperties;
    @Mock
    private AnalyticsProperties analyticsProperties;
    @Mock
    private ObjectManager objectManager;

    private AnalyticsEngineScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AnalyticsEngineScheduler(
                engineService,
                catalogService,
                scheduleRegistry,
                leaderLockService,
                clusterProperties,
                analyticsProperties,
                objectManager
        );
    }

    @Test
    void evaluationFailureDoesNotAdvanceDue() {
        AnalyticsTagDefinition tag = sampleTag();
        when(objectManager.isInitialized()).thenReturn(true);
        when(engineService.isEnabled()).thenReturn(true);
        when(clusterProperties.isSchedulerActive()).thenReturn(true);
        when(leaderLockService.runIfLeader(eq("analytics_engine_scheduler"), eq(Duration.ofSeconds(30)), any()))
                .thenAnswer(invocation -> {
                    invocation.<Runnable>getArgument(2).run();
                    return true;
                });
        when(scheduleRegistry.dueTagPaths(any())).thenReturn(List.of(TAG_PATH));
        when(catalogService.listEnabledTags()).thenReturn(List.of(tag));
        doThrow(new RuntimeException("historian unavailable"))
                .when(engineService)
                .evaluateTags(List.of(tag));

        assertThatCode(() -> scheduler.tick()).doesNotThrowAnyException();

        verify(scheduleRegistry, never()).markRan(any(), anyLong(), any(Instant.class), any());
    }

    private static AnalyticsTagDefinition sampleTag() {
        return new AnalyticsTagDefinition(
                TAG_PATH,
                "rollingAvg",
                List.of(new AnalyticsSourceRef("root.platform.devices.sensor-a", "temperature", "value")),
                "5m",
                PERIODIC_MS,
                false,
                true,
                "derivedValue"
        );
    }
}
