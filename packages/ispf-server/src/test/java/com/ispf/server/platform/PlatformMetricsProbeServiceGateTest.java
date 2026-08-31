package com.ispf.server.platform;

import com.ispf.server.config.PlatformMetricsProbeProperties;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformMetricsProbeServiceGateTest {

    @Mock
    PlatformMetricsProbeProperties properties;
    @Mock
    PlatformMetricsService metricsService;
    @Mock
    ObjectManager objectManager;

    private PlatformMetricsProbeService probeService;

    @BeforeEach
    void setUp() {
        probeService = new PlatformMetricsProbeService(properties, metricsService, objectManager);
    }

    @Test
    void pollSkipsWhenObjectTreeNotReady() {
        when(objectManager.isInitialized()).thenReturn(false);

        probeService.poll();

        verify(metricsService, never()).snapshot();
    }
}
