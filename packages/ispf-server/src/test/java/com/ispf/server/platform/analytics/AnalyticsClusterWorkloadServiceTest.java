package com.ispf.server.platform.analytics;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.ReplicaCapability;
import com.ispf.server.config.ReplicaProfile;
import com.ispf.server.platform.ClusterReplicaRegistryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsClusterWorkloadServiceTest {

    @Mock
    ClusterProperties clusterProperties;

    @Mock
    ClusterReplicaRegistryService replicaRegistry;

    @Test
    void analyticsProfileAlwaysActive() {
        when(clusterProperties.hasCapability(ReplicaCapability.ANALYTICS)).thenReturn(true);
        var service = new AnalyticsClusterWorkloadService(clusterProperties, replicaRegistry);
        assertTrue(service.isAnalyticsWorkloadActive());
    }

    @Test
    void singleNodeUnifiedRunsAnalytics() {
        when(clusterProperties.hasCapability(ReplicaCapability.ANALYTICS)).thenReturn(false);
        when(clusterProperties.enabled()).thenReturn(false);
        var service = new AnalyticsClusterWorkloadService(clusterProperties, replicaRegistry);
        assertTrue(service.isAnalyticsWorkloadActive());
    }

    @Test
    void ioReplicaDefersWhenAnalyticsReplicasUp() {
        when(clusterProperties.hasCapability(ReplicaCapability.ANALYTICS)).thenReturn(false);
        when(clusterProperties.enabled()).thenReturn(true);
        when(replicaRegistry.hasUpReplicaWithCapability(ReplicaCapability.ANALYTICS)).thenReturn(true);
        var service = new AnalyticsClusterWorkloadService(clusterProperties, replicaRegistry);
        assertFalse(service.isAnalyticsWorkloadActive());
    }

    @Test
    void unifiedFallbackWhenNoAnalyticsReplicas() {
        when(clusterProperties.hasCapability(ReplicaCapability.ANALYTICS)).thenReturn(false);
        when(clusterProperties.enabled()).thenReturn(true);
        when(replicaRegistry.hasUpReplicaWithCapability(ReplicaCapability.ANALYTICS)).thenReturn(false);
        when(clusterProperties.parsedReplicaProfile()).thenReturn(ReplicaProfile.UNIFIED);
        var service = new AnalyticsClusterWorkloadService(clusterProperties, replicaRegistry);
        assertTrue(service.isAnalyticsWorkloadActive());
    }

    @Test
    void edgeApiDoesNotRunAnalyticsWhenDedicatedReplicasExist() {
        when(clusterProperties.hasCapability(ReplicaCapability.ANALYTICS)).thenReturn(false);
        when(clusterProperties.enabled()).thenReturn(true);
        when(replicaRegistry.hasUpReplicaWithCapability(ReplicaCapability.ANALYTICS)).thenReturn(true);
        var service = new AnalyticsClusterWorkloadService(clusterProperties, replicaRegistry);
        assertFalse(service.isAnalyticsWorkloadActive());
    }
}
