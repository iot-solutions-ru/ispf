package com.ispf.server.platform;

import com.ispf.server.driver.DriverRuntimeService;
import com.ispf.server.event.EventHistoryRecordCounter;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.persistence.VariableSampleRepository;
import com.ispf.server.persistence.WorkflowInstanceRepository;
import com.ispf.server.websocket.ObjectWebSocketHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F-06: bind gauges on a {@link SimpleMeterRegistry} without full server boot.
 */
@ExtendWith(MockitoExtension.class)
class PlatformPrometheusMetricsBinderTest {

    @Mock
    private EventHistoryRecordCounter eventHistoryRecordCounter;
    @Mock
    private WorkflowInstanceRepository workflowInstanceRepository;
    @Mock
    private VariableSampleRepository variableSampleRepository;
    @Mock
    private DriverRuntimeService driverRuntimeService;
    @Mock
    private ObjectWebSocketHandler objectWebSocketHandler;
    @Mock
    private ObjectManager objectManager;
    @Mock
    private DataSource dataSource;

    @Test
    void registersPlatformStateGauges() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AutomationMetricsRecorder automationMetricsRecorder = new AutomationMetricsRecorder(Optional.of(meterRegistry));

        PlatformPrometheusMetricsBinder binder = new PlatformPrometheusMetricsBinder(
                Optional.of(meterRegistry),
                eventHistoryRecordCounter,
                workflowInstanceRepository,
                variableSampleRepository,
                driverRuntimeService,
                objectWebSocketHandler,
                objectManager,
                automationMetricsRecorder,
                dataSource
        );
        binder.bindGauges();

        assertThat(meterRegistry.find("ispf.event_history.records").gauge()).isNotNull();
        assertThat(meterRegistry.find("ispf.workflow_instances.running").gauge()).isNotNull();
        assertThat(meterRegistry.find("ispf.variable_history.samples").gauge()).isNotNull();
        assertThat(meterRegistry.find("ispf.drivers.active").gauge()).isNotNull();
        assertThat(meterRegistry.find("ispf.websocket.clients").gauge()).isNotNull();
        assertThat(meterRegistry.find("ispf.object_tree.ready").gauge()).isNotNull();
        assertThat(meterRegistry.find("ispf.object_change.queue.size").tag("lane", "total").gauge()).isNotNull();
        assertThat(meterRegistry.find("ispf.alert.fires.total").counter()).isNotNull();
    }
}
