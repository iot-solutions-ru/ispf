package com.ispf.server.platform;

import com.ispf.server.config.BootstrapProperties;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.persistence.ObjectNodeRepository;
import com.ispf.server.persistence.entity.ObjectNodeEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClusterPlatformBootstrapServiceTest {

    @Mock
    private PlatformLeaderLockService leaderLockService;

    @Mock
    private ObjectNodeRepository nodeRepository;

    private ClusterPlatformBootstrapService service;

    @BeforeEach
    void setUp() {
        BootstrapProperties bootstrapProperties = new BootstrapProperties();
        bootstrapProperties.setFixturesEnabled(true);
        service = new ClusterPlatformBootstrapService(
                new ClusterProperties(true, true, 30, 10, 15, 10, 30, true, true, 500, true, "", "", "all", true, 2000, 2, true, 1, 8, 50, 6, 500, 1800),
                bootstrapProperties,
                leaderLockService,
                nodeRepository
        );
    }

    @Test
    void singleNodeWithFixturesAlwaysRunsBootstrap() {
        BootstrapProperties bootstrapProperties = new BootstrapProperties();
        bootstrapProperties.setFixturesEnabled(true);
        ClusterPlatformBootstrapService singleNode = new ClusterPlatformBootstrapService(
                new ClusterProperties(false, true, 30, 10, 15, 10, 30, true, true, 500, true, "", "", "all", true, 2000, 2, true, 1, 8, 50, 6, 500, 1800),
                bootstrapProperties,
                leaderLockService,
                nodeRepository
        );
        assertThat(singleNode.shouldRunFixtureBootstrap()).isTrue();
        verify(leaderLockService, never()).tryAcquire(any(), any());
    }

    @Test
    void clusterElectsSingleFixtureLeader() {
        when(leaderLockService.tryAcquire(eq(ClusterPlatformBootstrapService.FIXTURE_BOOTSTRAP_LOCK), any()))
                .thenReturn(true)
                .thenReturn(false);

        BootstrapProperties bootstrapProperties = new BootstrapProperties();
        bootstrapProperties.setFixturesEnabled(true);
        ClusterPlatformBootstrapService follower = new ClusterPlatformBootstrapService(
                new ClusterProperties(true, true, 30, 10, 15, 10, 30, true, true, 500, true, "", "", "all", true, 2000, 2, true, 1, 8, 50, 6, 500, 1800),
                bootstrapProperties,
                leaderLockService,
                nodeRepository
        );

        assertThat(service.shouldRunFixtureBootstrap()).isTrue();
        assertThat(follower.shouldRunFixtureBootstrap()).isFalse();
    }

    @Test
    void followerWaitsForStableDeviceCount() {
        BootstrapProperties bootstrapProperties = new BootstrapProperties();
        bootstrapProperties.setFixturesEnabled(true);
        ClusterPlatformBootstrapService follower = new ClusterPlatformBootstrapService(
                new ClusterProperties(true, true, 30, 10, 15, 10, 30, true, true, 500, true, "", "", "all", true, 2000, 2, true, 1, 8, 50, 6, 500, 1800),
                bootstrapProperties,
                leaderLockService,
                nodeRepository
        );
        when(nodeRepository.findAllByOrderByPathAsc())
                .thenReturn(List.of())
                .thenReturn(List.of(deviceNode("root.platform.devices.gpu-01")))
                .thenReturn(List.of(deviceNode("root.platform.devices.gpu-01")))
                .thenReturn(List.of(deviceNode("root.platform.devices.gpu-01")));

        when(leaderLockService.tryAcquire(eq(ClusterPlatformBootstrapService.FIXTURE_BOOTSTRAP_LOCK), any()))
                .thenReturn(false);

        follower.prepareFixtureBootstrapRole();
        follower.waitForFixtureBootstrapComplete();

        verify(nodeRepository, org.mockito.Mockito.atLeast(3)).findAllByOrderByPathAsc();
    }

    private static ObjectNodeEntity deviceNode(String path) {
        ObjectNodeEntity entity = new ObjectNodeEntity();
        entity.setPath(path);
        return entity;
    }
}
