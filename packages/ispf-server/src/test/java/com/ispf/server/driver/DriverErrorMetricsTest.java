package com.ispf.server.driver;

import com.ispf.driver.DriverConfigurationException;
import com.ispf.driver.DriverErrorKind;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverTransientException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DriverErrorMetricsTest {

    @Test
    void recordsCounterPerDriverOperationAndKind() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DriverErrorMetrics metrics = new DriverErrorMetrics(Optional.of(registry));

        assertThat(metrics.record("modbus-tcp", DriverErrorMetrics.Operation.POLL, new DriverTransientException("t/o")))
                .isEqualTo(DriverErrorKind.TRANSIENT);
        metrics.record("modbus-tcp", DriverErrorMetrics.Operation.POLL, new DriverTransientException("t/o again"));
        metrics.record("modbus-tcp", DriverErrorMetrics.Operation.WRITE, new DriverConfigurationException("bad reg"));
        metrics.record(null, DriverErrorMetrics.Operation.CONNECT, new DriverException("plain"));

        assertThat(registry.get(DriverErrorMetrics.METRIC_NAME)
                .tags("driver", "modbus-tcp", "op", "poll", "kind", "transient")
                .counter().count()).isEqualTo(2.0);
        assertThat(registry.get(DriverErrorMetrics.METRIC_NAME)
                .tags("driver", "modbus-tcp", "op", "write", "kind", "configuration")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get(DriverErrorMetrics.METRIC_NAME)
                .tags("driver", "unknown", "op", "connect", "kind", "unclassified")
                .counter().count()).isEqualTo(1.0);

        assertThat(metrics.totals())
                .containsEntry(DriverErrorKind.TRANSIENT, 2L)
                .containsEntry(DriverErrorKind.CONFIGURATION, 1L)
                .containsEntry(DriverErrorKind.UNCLASSIFIED, 1L)
                .containsEntry(DriverErrorKind.PERMANENT, 0L);
    }

    @Test
    void worksWithoutMeterRegistry() {
        DriverErrorMetrics metrics = new DriverErrorMetrics(Optional.empty());
        metrics.record("snmp", DriverErrorMetrics.Operation.POLL, new IllegalStateException("boom"));
        assertThat(metrics.totals()).containsEntry(DriverErrorKind.UNCLASSIFIED, 1L);
    }
}
