package com.ispf.server.object;

import com.ispf.server.config.RuntimeTelemetryProperties;
import com.ispf.server.driver.DeviceTelemetryPolicyService;
import com.ispf.server.function.MqttGatewayIngressDispatchService;
import com.ispf.server.history.TelemetryHistorianFastPath;
import com.ispf.server.object.pubsub.ObjectChangePublicationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class RuntimeTelemetryCoalescerIdleTest {

    @Mock
    private ObjectChangePublicationService publicationService;

    @Mock
    private MqttGatewayIngressDispatchService gatewayIngressDispatch;

    @Mock
    private TelemetryHistorianFastPath historianFastPath;

    @Mock
    private TelemetryIngressDispatcher telemetryIngressDispatcher;

    private RuntimeTelemetryCoalescer coalescer;

    @AfterEach
    void tearDown() {
        if (coalescer != null) {
            coalescer.shutdown();
        }
    }

    @Test
    void doesNotStartSchedulerAtConstruction() {
        RuntimeTelemetryProperties properties = new RuntimeTelemetryProperties();
        properties.setEnabled(true);
        properties.setCoalesceEnabled(true);
        DeviceTelemetryPolicyService policyService = org.mockito.Mockito.mock(DeviceTelemetryPolicyService.class);
        coalescer = new RuntimeTelemetryCoalescer(
                properties, policyService, publicationService, gatewayIngressDispatch, historianFastPath,
                telemetryIngressDispatcher, null, java.util.Optional.empty()
        );

        assertThat(coalescer.isSchedulerStarted()).isFalse();
    }
}
