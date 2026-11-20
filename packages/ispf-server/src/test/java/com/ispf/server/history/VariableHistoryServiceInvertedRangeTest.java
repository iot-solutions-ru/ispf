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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VariableHistoryServiceInvertedRangeTest {

    private static final String PATH = "root.platform.devices.inverted-range";

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
                "inverted-range",
                PATH,
                ObjectType.DEVICE,
                "inverted range",
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
        lenient().when(objectManager.tree()).thenReturn(tree);
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
                parquetExport,
                mock(org.springframework.beans.factory.ObjectProvider.class)
        );
    }

    @Test
    void invertedRangeReturnsNoBuckets() {
        Instant from = Instant.parse("2026-11-17T15:11:13Z");
        Instant to = Instant.parse("2026-11-17T13:12:13Z");

        var response = service.aggregate(PATH, "temperature", "value", from, to, "5m", 10);

        assertThat(response.buckets()).isEmpty();
        assertThat(response.dataSource()).isEqualTo("none");
        verify(queryStore, never()).aggregateBuckets(
                any(), any(), any(), any(), any(), any(Duration.class), any(Integer.class)
        );
    }

    @Test
    void forwardRangeStillQueriesTheRequestedWindow() {
        Instant from = Instant.parse("2026-11-17T13:00:00Z");
        Instant to = Instant.parse("2026-11-17T15:00:00Z");
        when(queryStore.aggregateBuckets(
                eq(PATH), eq("temperature"), eq("value"), eq(from), eq(to), eq(Duration.ofMinutes(5)), eq(10)
        )).thenReturn(List.of());

        var response = service.aggregate(PATH, "temperature", "value", from, to, "5m", 10);

        assertThat(response.buckets()).isEmpty();
        assertThat(response.dataSource()).isEqualTo("raw");
    }
}
