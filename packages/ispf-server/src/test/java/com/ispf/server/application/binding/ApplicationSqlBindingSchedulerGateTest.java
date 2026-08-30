package com.ispf.server.application.binding;

import com.ispf.server.binding.SqlBindingObjectService;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.object.ObjectManager;
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
class ApplicationSqlBindingSchedulerGateTest {

    @Mock
    ApplicationSqlBindingService bindingService;
    @Mock
    SqlBindingObjectService sqlBindingObjectService;
    @Mock
    PlatformLeaderLockService leaderLockService;
    @Mock
    ClusterProperties clusterProperties;
    @Mock
    ObjectManager objectManager;

    private ApplicationSqlBindingScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ApplicationSqlBindingScheduler(
                bindingService,
                sqlBindingObjectService,
                leaderLockService,
                clusterProperties,
                objectManager
        );
    }

    @Test
    void skipsWhenObjectTreeNotReady() {
        when(objectManager.isInitialized()).thenReturn(false);

        scheduler.refreshScheduledBindings();

        verify(clusterProperties, never()).isSchedulerActive();
        verify(leaderLockService, never()).tryAcquire(any(), any());
        verify(bindingService, never()).refreshScheduledBindings();
    }
}
