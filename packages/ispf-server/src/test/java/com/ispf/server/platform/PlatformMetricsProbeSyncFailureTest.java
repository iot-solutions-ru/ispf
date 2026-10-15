package com.ispf.server.platform;

import com.ispf.core.model.DataRecord;
import com.ispf.server.config.PlatformMetricsProbeProperties;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformMetricsProbeSyncFailureTest {

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
    void snapshotFailureLeavesTheProbeStale() {
        when(metricsService.snapshot()).thenThrow(new IllegalStateException("store down"));

        probeService.syncOnce();

        assertThat(probeService.syncStatus()).isEqualTo("error");
        assertThat(probeService.isStale()).isTrue();
        assertThat(probeService.syncError()).contains("store down");
        verify(objectManager, never()).setSystemVariableValue(anyString(), anyString(), any(DataRecord.class));
    }

    @Test
    void laterSuccessClearsTheStaleFailure() {
        when(metricsService.snapshot())
                .thenThrow(new IllegalStateException("store down"))
                .thenReturn(snapshot());

        probeService.syncOnce();
        assertThat(probeService.isStale()).isTrue();

        probeService.syncOnce();

        assertThat(probeService.syncStatus()).isEqualTo("ok");
        assertThat(probeService.isStale()).isFalse();
        assertThat(probeService.syncError()).isEmpty();
    }

    private static Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("runtime", Map.of("heapUsedMb", 1.5));
        snapshot.put("database", Map.of());
        snapshot.put("drivers", Map.of());
        snapshot.put("automation", Map.of("eventHistoryRecords", 3L, "alertFiresTotal", 1L));
        snapshot.put("variableHistory", Map.of("sampleCount", 0L));
        snapshot.put("connections", Map.of("websocketClients", 0L));
        return snapshot;
    }
}
