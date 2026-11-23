package com.ispf.server.history;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.MssqlRelationalProperties;
import com.ispf.server.config.MysqlRelationalProperties;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VariableHistoryServiceRetentionClampTest {

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
    @Mock
    private ObjectProvider<MssqlRelationalProperties> mssqlRelationalProperties;
    @Mock
    private ObjectProvider<MysqlRelationalProperties> mysqlRelationalProperties;

    private VariableHistoryProperties properties;
    private VariableHistoryService service;

    @BeforeEach
    void setUp() {
        properties = new VariableHistoryProperties();
        properties.setRetentionDays(90);
        service = new VariableHistoryService(
                properties,
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
                mssqlRelationalProperties,
                mysqlRelationalProperties
        );
    }

    @Test
    void singleEngineClampsAboveCapAndUnlimited() {
        MssqlRelationalProperties mssql = new MssqlRelationalProperties();
        mssql.setSingleEngine(true);
        mssql.setMaxRetentionDays(30);
        when(mssqlRelationalProperties.getIfAvailable()).thenReturn(mssql);

        assertThat(service.clampRetentionDays(365)).isEqualTo(30);
        assertThat(service.clampRetentionDays(0)).isEqualTo(30);
        assertThat(service.clampRetentionDays(-1)).isEqualTo(30);
        assertThat(service.clampRetentionDays(15)).isEqualTo(15);
    }

    @Test
    void nonSingleEngineLeavesDaysAlone() {
        MssqlRelationalProperties mssql = new MssqlRelationalProperties();
        mssql.setSingleEngine(false);
        mssql.setMaxRetentionDays(30);
        when(mssqlRelationalProperties.getIfAvailable()).thenReturn(mssql);
        when(mysqlRelationalProperties.getIfAvailable()).thenReturn(new MysqlRelationalProperties());

        assertThat(service.clampRetentionDays(365)).isEqualTo(365);
        assertThat(service.clampRetentionDays(0)).isEqualTo(0);
    }

    @Test
    void missingMssqlBeanLeavesDaysAlone() {
        when(mssqlRelationalProperties.getIfAvailable()).thenReturn(null);
        assertThat(service.clampRetentionDays(365)).isEqualTo(365);
    }

    @Test
    void mysqlSingleEngineClampsAboveCapAndUnlimited() {
        when(mssqlRelationalProperties.getIfAvailable()).thenReturn(new MssqlRelationalProperties());
        MysqlRelationalProperties mysql = new MysqlRelationalProperties();
        mysql.setSingleEngine(true);
        when(mysqlRelationalProperties.getIfAvailable()).thenReturn(mysql);

        // Default ceiling is 90 days: application DELETE is the only MySQL retention path.
        assertThat(service.clampRetentionDays(365)).isEqualTo(90);
        assertThat(service.clampRetentionDays(0)).isEqualTo(90);
        assertThat(service.clampRetentionDays(-1)).isEqualTo(90);
        assertThat(service.clampRetentionDays(90)).isEqualTo(90);
        assertThat(service.clampRetentionDays(7)).isEqualTo(7);

        mysql.setMaxRetentionDays(30);
        assertThat(service.clampRetentionDays(31)).isEqualTo(30);
    }

    @Test
    void mysqlB1LeavesDaysAlone() {
        when(mssqlRelationalProperties.getIfAvailable()).thenReturn(null);
        MysqlRelationalProperties mysql = new MysqlRelationalProperties();
        mysql.setMaxRetentionDays(30);
        when(mysqlRelationalProperties.getIfAvailable()).thenReturn(mysql);

        assertThat(service.clampRetentionDays(365)).isEqualTo(365);
        assertThat(service.clampRetentionDays(0)).isEqualTo(0);
    }
}
