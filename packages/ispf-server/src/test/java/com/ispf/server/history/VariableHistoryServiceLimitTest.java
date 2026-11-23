package com.ispf.server.history;

import com.ispf.core.model.DataSchema;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.VariableHistoryProperties;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.persistence.ObjectVariableRepository;
import com.ispf.server.persistence.VariableSampleRepository;
import com.ispf.server.platform.PlatformLeaderLockService;
import com.ispf.server.platform.analytics.engine.AnalyticsOnChangeTrigger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VariableHistoryServiceLimitTest {

    private static final String PATH = "root.platform.devices.limit-room";

    @Mock
    private VariableSampleRepository sampleRepository;
    @Mock
    private ObjectVariableRepository variableRepository;
    @Mock
    private ObjectManager objectManager;
    @Mock
    private PlatformLeaderLockService leaderLockService;
    @Mock
    private ClusterProperties clusterProperties;
    @Mock
    private VariableHistoryAsyncWriter asyncWriter;
    @Mock
    private VariableHistoryBatchPersister batchPersister;
    @Mock
    private VariableHistoryQueryStore queryStore;
    @Mock
    private HistorianQueryMetricsRecorder queryMetricsRecorder;
    @Mock
    private ObjectProvider<AnalyticsOnChangeTrigger> analyticsOnChangeTrigger;
    @Mock
    private ObjectProvider<HistorianRollupQueryService> rollupQueryService;
    @Mock
    private ObjectProvider<HistorianRollupMaterializerService> rollupMaterializer;
    @Mock
    private HistoryParquetExportGateway parquetExport;

    private VariableHistoryService service;

    @BeforeEach
    void setUp() {
        ObjectTree tree = new ObjectTree();
        PlatformObject device = new PlatformObject(
                "limit-room",
                PATH,
                ObjectType.DEVICE,
                "limit room",
                null,
                null
        );
        device.addVariable(new Variable(
                "temperature",
                new DataSchema("temperature", List.of()),
                true,
                true,
                null,
                true,
                null
        ));
        tree.register(device);
        when(objectManager.require(PATH)).thenReturn(device);
        when(objectManager.tree()).thenReturn(tree);
        service = new VariableHistoryService(
                new VariableHistoryProperties(),
                sampleRepository,
                variableRepository,
                objectManager,
                new ObjectMapper(),
                leaderLockService,
                clusterProperties,
                asyncWriter,
                batchPersister,
                queryStore,
                queryMetricsRecorder,
                analyticsOnChangeTrigger,
                rollupQueryService,
                rollupMaterializer,
                mock(org.springframework.beans.factory.ObjectProvider.class),
                parquetExport,
                mock(org.springframework.beans.factory.ObjectProvider.class),
                mock(org.springframework.beans.factory.ObjectProvider.class),
                mock(org.springframework.beans.factory.ObjectProvider.class)
        );
    }

    @Test
    void zeroLimitReturnsNoSamples() {
        var response = service.query(PATH, "temperature", "value", null, null, 0);

        assertThat(response.samples()).isEmpty();
        verify(queryStore, never()).query(any(), any(), any(), any(), any(), any(Integer.class));
    }

    @Test
    void negativeLimitIsRejected() {
        assertThatThrownBy(() -> service.query(PATH, "temperature", "value", null, null, -3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("History limit must not be negative");
        verify(queryStore, never()).query(any(), any(), any(), any(), any(), any(Integer.class));
    }

    @Test
    void positiveLimitIsNotRaised() {
        when(queryStore.query(eq(PATH), eq("temperature"), eq("value"), any(), any(), eq(3)))
                .thenReturn(List.of(new VariableHistoryService.VariableHistorySample(
                        Instant.parse("2026-11-18T11:00:00Z"),
                        -20.0,
                        null
                )));

        var response = service.query(PATH, "temperature", "value", null, null, 3);

        assertThat(response.samples()).hasSize(1);
        assertThat(response.samples().get(0).value()).isEqualTo(-20.0);
    }

    @Test
    void zeroAggregateLimitReturnsNoBuckets() {
        Instant from = Instant.parse("2026-11-18T10:00:00Z");
        Instant to = Instant.parse("2026-11-18T12:00:00Z");

        var response = service.aggregate(PATH, "temperature", "value", from, to, "5m", 0);

        assertThat(response.buckets()).isEmpty();
        verify(queryStore, never()).aggregateBuckets(
                any(), any(), any(), any(), any(), any(Duration.class), any(Integer.class)
        );
    }

    @Test
    void negativeAggregateLimitIsRejected() {
        Instant from = Instant.parse("2026-11-18T10:00:00Z");
        Instant to = Instant.parse("2026-11-18T12:00:00Z");

        assertThatThrownBy(() -> service.aggregate(PATH, "temperature", "value", from, to, "5m", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("History limit must not be negative");
        verify(queryStore, never()).aggregateBuckets(
                any(), any(), any(), any(), any(), any(Duration.class), any(Integer.class)
        );
    }
}
