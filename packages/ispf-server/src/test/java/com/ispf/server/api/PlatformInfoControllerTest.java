package com.ispf.server.api;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.NatsProperties;
import com.ispf.server.config.ReplicaCapabilitySet;
import com.ispf.server.federation.FederationSecretsKeyService;
import com.ispf.server.history.HistoryParquetExportGateway;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformInfoControllerTest {

    @Test
    void infoReportsParquetExportUnavailableWhenModuleIsAbsent() {
        assertThat(info(false)).containsEntry("parquetExportAvailable", false);
    }

    @Test
    void infoReportsParquetExportAvailableWhenModuleIsPresent() {
        assertThat(info(true)).containsEntry("parquetExportAvailable", true);
    }

    private static Map<String, Object> info(boolean parquetAvailable) {
        NatsProperties nats = mock(NatsProperties.class);
        when(nats.replicaId()).thenReturn("local");
        ClusterProperties cluster = mock(ClusterProperties.class);
        when(cluster.enabled()).thenReturn(false);
        when(cluster.effectiveCapabilities()).thenReturn(ReplicaCapabilitySet.unified());
        when(cluster.isJobConsumerActive()).thenReturn(false);
        FederationSecretsKeyService secrets = mock(FederationSecretsKeyService.class);
        when(secrets.isConfigured()).thenReturn(false);
        when(secrets.source()).thenReturn(FederationSecretsKeyService.Source.NONE);
        HistoryParquetExportGateway parquetExport = mock(HistoryParquetExportGateway.class);
        when(parquetExport.isAvailable()).thenReturn(parquetAvailable);
        return new PlatformInfoController(
                Optional.empty(),
                secrets,
                nats,
                cluster,
                parquetExport,
                "test"
        ).info();
    }
}
