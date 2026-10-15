package com.ispf.server.operator;

import com.ispf.server.config.BootstrapProperties;
import com.ispf.server.dashboard.DashboardService;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

@ExtendWith(MockitoExtension.class)
class OperatorStarterTemplatesServiceSeedTest {

    @Mock
    private ObjectManager objectManager;
    @Mock
    private DashboardService dashboardService;
    @Mock
    private OperatorAppUiService operatorAppUiService;

    @Test
    void failedSeedIsVisibleAndDoesNotEscapeTheReadyListener() throws Exception {
        BootstrapProperties properties = new BootstrapProperties();
        properties.setFixturesEnabled(true);
        OperatorStarterTemplatesService service = spy(new OperatorStarterTemplatesService(
                objectManager,
                dashboardService,
                operatorAppUiService,
                properties
        ));
        doThrow(new IllegalStateException("dashboards missing")).when(service).installStarters(false);

        assertThatCode(service::seedWhenFixturesEnabled).doesNotThrowAnyException();

        assertThat(service.seedStatus())
                .containsEntry("required", true)
                .containsEntry("ready", false)
                .containsEntry("error", "dashboards missing");
    }
}
