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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VariableHistoryServiceObservedRollupHookTest {

    private static final String PATH = "root.platform.devices.obs-room";

    @Mock private VariableSampleRepository sampleRepository;
    @Mock private ObjectVariableRepository variableRepository;
    @Mock private ObjectManager objectManager;
    @Mock private PlatformLeaderLockService leaderLockService;
    @Mock private ClusterProperties clusterProperties;
    @Mock private VariableHistoryAsyncWriter asyncWriter;
    @Mock private VariableHistoryBatchPersister batchPersister;
    @Mock private VariableHistoryQueryStore queryStore;
    @Mock private HistorianQueryMetricsRecorder queryMetricsRecorder;
    @Mock private ObjectProvider<AnalyticsOnChangeTrigger> analyticsOnChangeTrigger;
    @Mock private ObjectProvider<HistorianRollupQueryService> rollupQueryService;
    @Mock private ObjectProvider<HistorianRollupMaterializerService> rollupMaterializer;
    @Mock private ObjectProvider<JdbcMssqlObservedAtRollupQueryService> observedAtRollupQueryService;
    @Mock private HistoryParquetExportGateway parquetExport;
    @Mock private JdbcMssqlObservedAtRollupQueryService observedSvc;

    private VariableHistoryService service;

    @BeforeEach
    void setUp() {
        ObjectTree tree = new ObjectTree();
        PlatformObject device = new PlatformObject(
                "obs-room",
                PATH,
                ObjectType.DEVICE,
                "obs room",
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
        when(rollupQueryService.getIfAvailable()).thenReturn(null);
        when(observedAtRollupQueryService.getIfAvailable()).thenReturn(observedSvc);

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
                observedAtRollupQueryService,
                parquetExport,
                mock(ObjectProvider.class),
                mock(ObjectProvider.class)
        );
    }

    @Test
    void aggregateUsesObservedRollupWhenPresent() {
        Instant from = Instant.parse("2026-02-01T00:00:00Z");
        Instant to = Instant.parse("2026-02-01T12:00:00Z");
        var buckets = List.of(
                new VariableHistoryService.VariableHistoryBucket(from, 1.0, 1.0, 1.0, 1)
        );
        when(observedSvc.tryAggregate(
                eq(PATH), eq("temperature"), eq("value"), eq(from), eq(to), eq(Duration.ofHours(1)), eq(100)
        )).thenReturn(Optional.of(new HistorianRollupQueryService.RollupQueryResult(buckets, "observed_rollup")));

        var response = service.aggregate(PATH, "temperature", "value", from, to, "1h", 100);
        assertThat(response.dataSource()).isEqualTo("observed_rollup");
        assertThat(response.buckets()).isEqualTo(buckets);
        verify(queryStore, never()).aggregateBuckets(any(), any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void aggregateFallsBackToRawWhenObservedEmpty() {
        Instant from = Instant.parse("2026-02-01T00:00:00Z");
        Instant to = Instant.parse("2026-02-01T12:00:00Z");
        when(observedSvc.tryAggregate(any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(Optional.empty());
        when(queryStore.aggregateBuckets(
                eq(PATH), eq("temperature"), eq("value"), eq(from), eq(to), eq(Duration.ofHours(1)), eq(100)
        )).thenReturn(List.of(
                new VariableHistoryService.VariableHistoryBucket(from, 2.0, 2.0, 2.0, 2)
        ));

        var response = service.aggregate(PATH, "temperature", "value", from, to, "1h", 100);
        assertThat(response.dataSource()).isEqualTo("raw");
        assertThat(response.buckets()).hasSize(1);
    }
}
