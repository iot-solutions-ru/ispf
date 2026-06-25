package com.ispf.server.platform;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.config.PlatformMetricsProbeProperties;
import com.ispf.server.object.ObjectManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class PlatformMetricsProbeService {

    private static final Logger log = LoggerFactory.getLogger(PlatformMetricsProbeService.class);

    static final String DEVICE_PATH = "root.platform.devices.platform-metrics-probe";

    private static final DataSchema INTEGER_VALUE = DataSchema.builder("integerValue")
            .field("value", FieldType.INTEGER)
            .build();
    private static final DataSchema DOUBLE_VALUE = DataSchema.builder("doubleValue")
            .field("value", FieldType.DOUBLE)
            .build();

    private final PlatformMetricsProbeProperties properties;
    private final PlatformMetricsService metricsService;
    private final ObjectManager objectManager;

    private Long lastEventHistoryRecords;
    private Long lastAlertFiresTotal;
    private Instant lastPollTime;

    public PlatformMetricsProbeService(
            PlatformMetricsProbeProperties properties,
            PlatformMetricsService metricsService,
            ObjectManager objectManager
    ) {
        this.properties = properties;
        this.metricsService = metricsService;
        this.objectManager = objectManager;
    }

    @EventListener(ApplicationReadyEvent.class)
    void bootstrap() {
        if (!properties.isEnabled()) {
            return;
        }
        if (!probeDeviceExists()) {
            log.debug("Platform metrics probe disabled: device {} not found", DEVICE_PATH);
            return;
        }
        log.info("Platform metrics probe syncing to {}", DEVICE_PATH);
        syncOnce();
    }

    @Scheduled(fixedDelayString = "${ispf.platform-metrics-probe.interval-ms:5000}")
    void poll() {
        if (!properties.isEnabled() || !probeDeviceExists()) {
            return;
        }
        syncOnce();
    }

    void syncOnce() {
        try {
            Map<String, Object> snapshot = metricsService.snapshot();
            @SuppressWarnings("unchecked")
            Map<String, Object> runtime = (Map<String, Object>) snapshot.getOrDefault("runtime", Map.of());
            @SuppressWarnings("unchecked")
            Map<String, Object> database = (Map<String, Object>) snapshot.getOrDefault("database", Map.of());
            @SuppressWarnings("unchecked")
            Map<String, Object> drivers = (Map<String, Object>) snapshot.getOrDefault("drivers", Map.of());
            @SuppressWarnings("unchecked")
            Map<String, Object> automation = (Map<String, Object>) snapshot.getOrDefault("automation", Map.of());
            @SuppressWarnings("unchecked")
            Map<String, Object> history = (Map<String, Object>) snapshot.getOrDefault("variableHistory", Map.of());

            long events = longValue(automation.get("eventHistoryRecords"));
            long alertFires = longValue(automation.get("alertFiresTotal"));
            RateSnapshot rates = computeRates(events, alertFires);

            writeInteger("eventHistoryRecords", events);
            writeDouble("eventsPerSecond", rates.eventsPerSecond());
            writeInteger("alertFiresTotal", alertFires);
            writeDouble("alertFiresPerSecond", rates.alertFiresPerSecond());
            writeInteger("objectChangeQueueSize", longValue(automation.get("objectChangeQueueSize")));
            writeDouble("heapUsedMb", doubleValue(runtime.get("heapUsedMb")));
            writeInteger("activeConnections", longValue(database.get("activeConnections")));
            writeInteger("threadsAwaitingConnection", longValue(database.get("threadsAwaitingConnection")));
            writeInteger("activeDrivers", longValue(drivers.get("activeDrivers")));
            writeInteger("workflowInstancesRunning", longValue(automation.get("workflowInstancesRunning")));
            writeInteger("variableHistorySamples", longValue(history.get("sampleCount")));
        } catch (Exception ex) {
            log.warn("Platform metrics probe sync failed: {}", ex.getMessage());
        }
    }

    private RateSnapshot computeRates(long events, long alertFires) {
        Instant now = Instant.now();
        double eventsRate = 0.0;
        double alertRate = 0.0;
        if (lastPollTime != null) {
            double seconds = (now.toEpochMilli() - lastPollTime.toEpochMilli()) / 1000.0;
            if (seconds > 0) {
                if (lastEventHistoryRecords != null) {
                    eventsRate = Math.max(0.0, (events - lastEventHistoryRecords) / seconds);
                }
                if (lastAlertFiresTotal != null) {
                    alertRate = Math.max(0.0, (alertFires - lastAlertFiresTotal) / seconds);
                }
            }
        }
        lastEventHistoryRecords = events;
        lastAlertFiresTotal = alertFires;
        lastPollTime = now;
        return new RateSnapshot(
                Math.round(eventsRate * 100.0) / 100.0,
                Math.round(alertRate * 100.0) / 100.0
        );
    }

    private record RateSnapshot(double eventsPerSecond, double alertFiresPerSecond) {}

    private boolean probeDeviceExists() {
        return objectManager.tree().findByPath(DEVICE_PATH).isPresent();
    }

    private void writeInteger(String name, long value) {
        objectManager.setSystemVariableValue(
                DEVICE_PATH,
                name,
                DataRecord.single(INTEGER_VALUE, Map.of("value", value))
        );
    }

    private void writeDouble(String name, double value) {
        objectManager.setSystemVariableValue(
                DEVICE_PATH,
                name,
                DataRecord.single(DOUBLE_VALUE, Map.of("value", value))
        );
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private static double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0;
    }
}
