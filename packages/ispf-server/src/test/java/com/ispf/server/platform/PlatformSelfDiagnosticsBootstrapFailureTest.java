package com.ispf.server.platform;

import com.ispf.core.object.ObjectTree;
import com.ispf.server.config.PlatformMetricsProbeProperties;
import com.ispf.server.dashboard.DashboardService;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformSelfDiagnosticsBootstrapFailureTest {

    @Mock
    PlatformMetricsProbeProperties properties;
    @Mock
    ObjectManager objectManager;
    @Mock
    DashboardService dashboardService;
    @Mock
    PlatformMetricsProbeService probeService;

    @Test
    void bootstrapFailureIsAReadinessError() {
        when(properties.isEnsureOnStartup()).thenReturn(true);
        ObjectTree tree = mock(ObjectTree.class);
        when(objectManager.tree()).thenReturn(tree);
        when(tree.findByPath(anyString())).thenThrow(new IllegalStateException("tree down"));
        PlatformSelfDiagnosticsBootstrap bootstrap = bootstrap();

        assertThatThrownBy(bootstrap::ensureSelfDiagnostics)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Self-diagnostics bootstrap failed")
                .hasMessageContaining("tree down");
    }

    @Test
    void disabledBootstrapDoesNotFailReadiness() {
        when(properties.isEnsureOnStartup()).thenReturn(false);
        PlatformSelfDiagnosticsBootstrap bootstrap = bootstrap();

        assertThatCode(bootstrap::ensureSelfDiagnostics).doesNotThrowAnyException();
    }

    private PlatformSelfDiagnosticsBootstrap bootstrap() {
        return new PlatformSelfDiagnosticsBootstrap(
                properties,
                objectManager,
                dashboardService,
                probeService
        );
    }
}
