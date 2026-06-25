package com.ispf.server.platform;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PlatformPrometheusMetricsBinderTest {

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void registersPlatformStateGauges() {
        assertThat(meterRegistry.find("ispf.event_history.records").gauge()).isNotNull();
        assertThat(meterRegistry.find("ispf.workflow_instances.running").gauge()).isNotNull();
        assertThat(meterRegistry.find("ispf.variable_history.samples").gauge()).isNotNull();
        assertThat(meterRegistry.find("ispf.drivers.active").gauge()).isNotNull();
        assertThat(meterRegistry.find("ispf.object_change.queue.size").tag("lane", "total").gauge()).isNotNull();
        assertThat(meterRegistry.find("ispf.alert.fires.total").counter()).isNotNull();
    }
}
