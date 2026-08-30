package com.ispf.server.application.schedule;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.driver.DriverRuntimeService;
import com.ispf.server.function.FunctionService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.PlatformLeaderLockService;
import com.ispf.server.schedule.ScheduleObjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformSchedulerServiceGateTest {

    @Mock
    JdbcTemplate jdbcTemplate;
    @Mock
    FunctionService functionService;
    @Mock
    DriverRuntimeService driverRuntimeService;
    @Mock
    PlatformLeaderLockService leaderLockService;
    @Mock
    ScheduleObjectService scheduleObjectService;
    @Mock
    ClusterProperties clusterProperties;
    @Mock
    ObjectManager objectManager;

    private PlatformSchedulerService scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PlatformSchedulerService(
                jdbcTemplate,
                functionService,
                driverRuntimeService,
                new ObjectMapper(),
                leaderLockService,
                scheduleObjectService,
                clusterProperties,
                objectManager
        );
    }

    @Test
    void tickSkipsWhenObjectTreeNotReady() {
        when(clusterProperties.isSchedulerActive()).thenReturn(true);
        when(objectManager.isInitialized()).thenReturn(false);

        scheduler.tick();

        verify(leaderLockService, never()).tryAcquire(any(), any());
    }
}
