package com.ispf.server.platform;

import com.ispf.core.object.ObjectTree;
import com.ispf.server.config.PlatformMetricsProbeProperties;
import com.ispf.server.dashboard.DashboardService;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    void bootstrapFailureMarksContourNotReadyWithoutThrowing() {
        when(properties.isEnsureOnStartup()).thenReturn(true);
        ObjectTree tree = mock(ObjectTree.class);
        when(objectManager.tree()).thenReturn(tree);
        when(tree.findByPath(anyString())).thenThrow(new IllegalStateException("tree down"));
        PlatformSelfDiagnosticsBootstrap bootstrap = bootstrap();

        assertThatCode(bootstrap::ensureSelfDiagnostics).doesNotThrowAnyException();

        verify(probeService).markBootstrapFailure(any(RuntimeException.class));
        verify(probeService, never()).markBootstrapOk();
        verify(probeService, never()).setDiagnosticsProbeEnabled(true);
    }

    @Test
    void disabledBootstrapMarksSkipped() {
        when(properties.isEnsureOnStartup()).thenReturn(false);
        PlatformSelfDiagnosticsBootstrap bootstrap = bootstrap();

        assertThatCode(bootstrap::ensureSelfDiagnostics).doesNotThrowAnyException();

        verify(probeService).markBootstrapSkipped();
        verify(probeService, never()).markBootstrapFailure(any());
    }

    @Test
    void enablingProbeAfterBootstrapFailureIsNotReady() {
        PlatformMetricsProbeService realProbe = new PlatformMetricsProbeService(
                properties,
                mock(PlatformMetricsService.class),
                objectManager
        );
        realProbe.markBootstrapFailure(new IllegalStateException("tree down"));

        assertThat(realProbe.isBootstrapReady()).isFalse();
        assertThat(realProbe.bootstrapStatus()).isEqualTo("error");
        assertThatThrownBy(() -> realProbe.setDiagnosticsProbeEnabled(true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Self-diagnostics is not ready")
                .hasMessageContaining("tree down");
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
