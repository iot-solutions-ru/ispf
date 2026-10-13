package com.ispf.server.platform;

import com.ispf.server.config.BootstrapProperties;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.object.PlatformObjectReadinessGate;
import com.ispf.server.persistence.ObjectNodeRepository;
import com.ispf.server.spi.ObjectTreeReadyOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Status;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClusterFixtureBootstrapFailureTest {

    @Mock
    private PlatformLeaderLockService leaderLockService;

    @Mock
    private ObjectNodeRepository nodeRepository;

    @Test
    void followerTimeoutDoesNotThrowAndMarksReadinessDown() {
        when(leaderLockService.tryAcquire(eq(ClusterPlatformBootstrapService.FIXTURE_BOOTSTRAP_LOCK), any()))
                .thenReturn(false);
        when(nodeRepository.findAllByOrderByPathAsc()).thenReturn(List.of());
        ClusterPlatformBootstrapService follower = follower();
        follower.setFixtureWait(2, Duration.ZERO);
        follower.prepareFixtureBootstrapRole();
        ClusterFixtureHealthIndicator health = new ClusterFixtureHealthIndicator(follower);

        assertThatCode(follower::waitForFixtureBootstrapComplete).doesNotThrowAnyException();

        assertThat(follower.isFixtureBootstrapReady()).isFalse();
        assertThat(follower.fixtureBootstrapFailure()).contains("Timed out").contains("0 device nodes");
        assertThat(health.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.health().getDetails()).containsEntry("error", follower.fixtureBootstrapFailure());
    }

    @Test
    void clusterOffStaysReady() {
        BootstrapProperties bootstrapProperties = new BootstrapProperties();
        bootstrapProperties.setFixturesEnabled(true);
        ClusterPlatformBootstrapService singleNode = new ClusterPlatformBootstrapService(
                cluster(false),
                bootstrapProperties,
                leaderLockService,
                nodeRepository
        );
        ClusterFixtureHealthIndicator health = new ClusterFixtureHealthIndicator(singleNode);

        assertThatCode(singleNode::waitForFixtureBootstrapComplete).doesNotThrowAnyException();

        assertThat(singleNode.isFixtureBootstrapReady()).isTrue();
        assertThat(singleNode.fixtureBootstrapFailure()).isEmpty();
        assertThat(health.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void followerWaitRunsBeforeTreeReady() throws Exception {
        Method method = PlatformObjectReadinessGate.class.getDeclaredMethod("syncFollowerTreeBeforeDrivers");
        Order order = method.getAnnotation(Order.class);
        assertThat(order).isNotNull();
        assertThat(order.value()).isLessThan(ObjectTreeReadyOrder.OBJECT_TREE_READY_ORDER);
    }

    private ClusterPlatformBootstrapService follower() {
        BootstrapProperties bootstrapProperties = new BootstrapProperties();
        bootstrapProperties.setFixturesEnabled(true);
        return new ClusterPlatformBootstrapService(
                cluster(true),
                bootstrapProperties,
                leaderLockService,
                nodeRepository
        );
    }

    private static ClusterProperties cluster(boolean enabled) {
        return new ClusterProperties(
                enabled, true, 30, 10, 15, 10, 30, true, 2, 8, 4, 6, 500, true, true, 500, true,
                "", "", "all", true, 2000, 2, true, 1, 8, 50, 6, 500, 1800
        );
    }
}
