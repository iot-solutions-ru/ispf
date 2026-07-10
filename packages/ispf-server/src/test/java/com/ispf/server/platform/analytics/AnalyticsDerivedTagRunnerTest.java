package com.ispf.server.platform.analytics;

import com.ispf.server.config.AnalyticsProperties;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.platform.PlatformLeaderLockService;
import com.ispf.server.platform.analytics.engine.AnalyticsEngineService;
import com.ispf.server.platform.analytics.engine.AnalyticsEngineScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsDerivedTagRunnerTest {

    @Mock
    private AnalyticsEngineService engineService;
    @Mock
    private AnalyticsEngineScheduler engineScheduler;
    @Mock
    private AnalyticsProperties analyticsProperties;
    @Mock
    private AnalyticsClusterWorkloadService analyticsClusterWorkloadService;
    @Mock
    private PlatformLeaderLockService leaderLockService;
    @Mock
    private ClusterProperties clusterProperties;

    private AnalyticsDerivedTagRunner runner;

    @BeforeEach
    void setUp() {
        runner = new AnalyticsDerivedTagRunner(
                engineService,
                engineScheduler,
                analyticsProperties,
                analyticsClusterWorkloadService,
                leaderLockService,
                clusterProperties
        );
    }

    @Test
    void runsOnAnalyticsWorkloadReplica() {
        when(analyticsProperties.derivedTagEnabled()).thenReturn(true);
        when(engineService.isEnabled()).thenReturn(true);
        when(analyticsClusterWorkloadService.isAnalyticsWorkloadActive()).thenReturn(true);
        when(clusterProperties.isSchedulerActive()).thenReturn(true);
        when(leaderLockService.tryAcquire("analytics_derived_tag_runner", Duration.ofSeconds(90)))
                .thenReturn(true);

        runner.tick();

        verify(engineScheduler).syncSchedules();
        verify(engineService).evaluateAllEnabled();
        verify(leaderLockService).release("analytics_derived_tag_runner");
    }

    @Test
    void skipsWhenEdgeReplicaAndDedicatedAnalyticsExists() {
        when(analyticsProperties.derivedTagEnabled()).thenReturn(true);
        when(engineService.isEnabled()).thenReturn(true);
        when(analyticsClusterWorkloadService.isAnalyticsWorkloadActive()).thenReturn(false);

        runner.tick();

        verify(engineScheduler, never()).syncSchedules();
        verify(engineService, never()).evaluateAllEnabled();
        verify(leaderLockService, never()).tryAcquire(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void skipsWhenDerivedTagDisabled() {
        when(analyticsProperties.derivedTagEnabled()).thenReturn(false);

        runner.tick();

        verify(analyticsClusterWorkloadService, never()).isAnalyticsWorkloadActive();
        verify(engineService, never()).evaluateAllEnabled();
    }

    @Test
    void skipsWhenLeaderLockNotAcquired() {
        when(analyticsProperties.derivedTagEnabled()).thenReturn(true);
        when(engineService.isEnabled()).thenReturn(true);
        when(analyticsClusterWorkloadService.isAnalyticsWorkloadActive()).thenReturn(true);
        when(clusterProperties.isSchedulerActive()).thenReturn(true);
        when(leaderLockService.tryAcquire("analytics_derived_tag_runner", Duration.ofSeconds(90)))
                .thenReturn(false);

        runner.tick();

        verify(engineService, never()).evaluateAllEnabled();
        verify(leaderLockService, never()).release("analytics_derived_tag_runner");
    }
}
