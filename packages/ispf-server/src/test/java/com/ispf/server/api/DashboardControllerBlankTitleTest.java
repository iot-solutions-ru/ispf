package com.ispf.server.api;

import com.ispf.server.dashboard.DashboardService;
import com.ispf.server.federation.FederationProxyService;
import com.ispf.server.security.acl.VariableMemberAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardControllerBlankTitleTest {

    private static final String PATH = "root.platform.dashboards.fresh";

    @Mock
    private DashboardService dashboardService;

    @Mock
    private FederationProxyService federationProxyService;

    @Mock
    private VariableMemberAccessService variableMemberAccessService;

    @Test
    void blankTitleIsRejectedBeforeItIsSaved() {
        DashboardController controller = controller();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> controller.saveTitle(PATH, new DashboardController.SaveTitleRequest(" "), null)
        );

        assertEquals("title must not be blank", error.getMessage());
        verify(dashboardService, never()).updateTitle(any(), any());
        verify(federationProxyService, never()).proxyDashboardSaveTitle(any(), any());
        verify(variableMemberAccessService, never()).requireWrite(anyString(), anyString(), any());
    }

    @Test
    void emptyTitleIsRejectedBeforeItIsSaved() {
        DashboardController controller = controller();

        assertThrows(
                IllegalArgumentException.class,
                () -> controller.saveTitle(PATH, new DashboardController.SaveTitleRequest(""), null)
        );
        verify(dashboardService, never()).updateTitle(any(), any());
    }

    @Test
    void aRealTitleIsSaved() {
        when(federationProxyService.resolve(PATH)).thenReturn(Optional.empty());
        when(dashboardService.updateTitle(PATH, "cc-wave-3121-t"))
                .thenReturn(new DashboardService.DashboardView(PATH, "cc-wave-3121-t", 5000, null, "{}"));
        DashboardController controller = controller();

        DashboardService.DashboardView saved = controller.saveTitle(
                PATH,
                new DashboardController.SaveTitleRequest("cc-wave-3121-t"),
                null
        );

        assertEquals("cc-wave-3121-t", saved.title());
        verify(dashboardService).updateTitle(PATH, "cc-wave-3121-t");
    }

    private DashboardController controller() {
        return new DashboardController(dashboardService, federationProxyService, null, variableMemberAccessService);
    }
}
