package com.ispf.server.platform;

import com.ispf.server.driver.DriverRuntimeService;
import com.ispf.server.persistence.EventHistoryRepository;
import com.ispf.server.persistence.VariableSampleRepository;
import com.ispf.server.persistence.WorkflowInstanceRepository;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Optional;

/**
 * Registers platform state gauges on {@link MeterRegistry} for {@code /actuator/prometheus}.
 * Complements runtime counters in {@link AutomationMetricsRecorder}.
 */
@Component
public class PlatformPrometheusMetricsBinder {

    private final Optional<MeterRegistry> meterRegistry;
    private final EventHistoryRepository eventHistoryRepository;
    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final VariableSampleRepository variableSampleRepository;
    private final DriverRuntimeService driverRuntimeService;
    private final AutomationMetricsRecorder automationMetricsRecorder;
    private final DataSource dataSource;

    public PlatformPrometheusMetricsBinder(
            Optional<MeterRegistry> meterRegistry,
            EventHistoryRepository eventHistoryRepository,
            WorkflowInstanceRepository workflowInstanceRepository,
            VariableSampleRepository variableSampleRepository,
            DriverRuntimeService driverRuntimeService,
            AutomationMetricsRecorder automationMetricsRecorder,
            DataSource dataSource
    ) {
        this.meterRegistry = meterRegistry;
        this.eventHistoryRepository = eventHistoryRepository;
        this.workflowInstanceRepository = workflowInstanceRepository;
        this.variableSampleRepository = variableSampleRepository;
        this.driverRuntimeService = driverRuntimeService;
        this.automationMetricsRecorder = automationMetricsRecorder;
        this.dataSource = dataSource;
    }

    @PostConstruct
    void bindGauges() {
        meterRegistry.ifPresent(registry -> {
            Gauge.builder("ispf.event_history.records", eventHistoryRepository, EventHistoryRepository::count)
                    .description("Rows in event_history table")
                    .register(registry);

            Gauge.builder("ispf.workflow_instances.running", workflowInstanceRepository,
                            repo -> repo.countByStatus("RUNNING"))
                    .description("Workflow instances in RUNNING status")
                    .register(registry);

            Gauge.builder("ispf.variable_history.samples", variableSampleRepository, VariableSampleRepository::count)
                    .description("Rows in variable_samples historian table")
                    .register(registry);

            Gauge.builder("ispf.drivers.active", this, PlatformPrometheusMetricsBinder::activeDrivers)
                    .description("Configured active driver runtimes")
                    .register(registry);

            Gauge.builder("ispf.drivers.connected", this, PlatformPrometheusMetricsBinder::connectedDrivers)
                    .description("Active drivers reporting connected")
                    .register(registry);

            Gauge.builder("ispf.object_change.queue.size", automationMetricsRecorder,
                            AutomationMetricsRecorder::objectChangeQueueSize)
                    .tags(Tags.of("lane", "total"))
                    .description("Sum of object-change async queue depths (all lanes)")
                    .register(registry);

            bindHikariGauges(registry);
        });
    }

    private void bindHikariGauges(MeterRegistry registry) {
        if (!(dataSource instanceof HikariDataSource hikari)) {
            return;
        }
        HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
        if (pool == null) {
            return;
        }
        Gauge.builder("ispf.database.connections.active", pool, HikariPoolMXBean::getActiveConnections)
                .register(registry);
        Gauge.builder("ispf.database.connections.idle", pool, HikariPoolMXBean::getIdleConnections)
                .register(registry);
        Gauge.builder("ispf.database.connections.awaiting", pool, HikariPoolMXBean::getThreadsAwaitingConnection)
                .register(registry);
    }

    private double activeDrivers() {
        return driverMetric("activeDrivers");
    }

    private double connectedDrivers() {
        return driverMetric("connectedDrivers");
    }

    private double driverMetric(String key) {
        Object value = driverRuntimeService.runtimeMetrics().get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0;
    }
}
