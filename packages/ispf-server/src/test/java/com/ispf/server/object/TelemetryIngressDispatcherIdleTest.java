package com.ispf.server.object;

import com.ispf.server.config.RuntimeTelemetryProperties;
import com.ispf.server.platform.AutomationMetricsRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TelemetryIngressDispatcherIdleTest {

    @Mock
    private RuntimeTelemetryCoalescer coalescer;

    @Mock
    private AutomationMetricsRecorder metrics;

    private TelemetryIngressDispatcher dispatcher;

    @AfterEach
    void tearDown() {
        if (dispatcher != null) {
            dispatcher.shutdown();
        }
    }

    @Test
    void doesNotStartWorkersUntilFirstSubmit() {
        RuntimeTelemetryProperties properties = new RuntimeTelemetryProperties();
        properties.setIngressQueueEnabled(true);
        dispatcher = new TelemetryIngressDispatcher(properties, coalescer, metrics);

        assertThat(dispatcher.isStarted()).isFalse();
    }
}
