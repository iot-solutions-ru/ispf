package com.ispf.server.platform.analytics;

import com.ispf.server.config.AnalyticsProperties;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.PlatformLeaderLockService;
import com.ispf.server.platform.analytics.engine.AnalyticsEngineScheduler;
import com.ispf.server.platform.analytics.engine.AnalyticsEngineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsDerivedTagRunnerGateTest {

    @Mock
    AnalyticsEngineService engineService;
    @Mock
    AnalyticsEngineScheduler engineScheduler;
    @Mock
    AnalyticsProperties analyticsProperties;
    @Mock
    AnalyticsClusterWorkloadService analyticsClusterWorkloadService;
    @Mock
    PlatformLeaderLockService leaderLockService;
    @Mock
    ClusterProperties clusterProperties;
    @Mock
    ObjectManager objectManager;

    private AnalyticsDerivedTagRunner runner;

    @BeforeEach
    void setUp() {
        runner = new AnalyticsDerivedTagRunner(
                engineService,
                engineScheduler,
                analyticsProperties,
                analyticsClusterWorkloadService,
                leaderLockService,
                clusterProperties,
                objectManager
        );
    }

    @Test
    void tickSkipsWhenObjectTreeNotReady() {
        when(analyticsProperties.derivedTagEnabled()).thenReturn(true);
        when(engineService.isEnabled()).thenReturn(true);
        when(objectManager.isInitialized()).thenReturn(false);

        runner.tick();

        verify(leaderLockService, never()).tryAcquire(any(), any());
        verify(engineScheduler, never()).syncSchedules();
    }
}
