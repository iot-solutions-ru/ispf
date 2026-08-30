package com.ispf.server.object;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.platform.PlatformLeaderLockService;
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
class BindingPeriodicSchedulerGateTest {

    @Mock
    BindingPeriodicScheduleRegistry registry;
    @Mock
    BindingRuleEngine bindingRuleEngine;
    @Mock
    PlatformLeaderLockService leaderLockService;
    @Mock
    ClusterProperties clusterProperties;
    @Mock
    ObjectManager objectManager;

    private BindingPeriodicScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new BindingPeriodicScheduler(
                registry,
                bindingRuleEngine,
                leaderLockService,
                clusterProperties,
                objectManager
        );
    }

    @Test
    void tickSkipsFireWhenObjectTreeNotReady() {
        when(objectManager.isInitialized()).thenReturn(false);
        when(registry.countEnabled()).thenReturn(0);

        scheduler.tick();

        verify(leaderLockService, never()).tryAcquire(any(), any());
        verify(registry, never()).fireDue(any(), any());
    }

    @Test
    void leaderFailoverProbeSkipsWhenObjectTreeNotReady() {
        when(objectManager.isInitialized()).thenReturn(false);

        scheduler.leaderFailoverProbe();

        verify(registry, never()).countEnabled();
    }
}
