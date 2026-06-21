package com.ispf.server.driver;

import tools.jackson.databind.ObjectMapper;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.ObjectType;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.server.bootstrap.LabTrainingBundleLayouts;
import com.ispf.server.object.ObjectManager;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class DriverRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(DriverRuntimeService.class);
    private static final DataSchema STRING_VALUE_SCHEMA = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();
    private static final DataSchema INTEGER_VALUE_SCHEMA = DataSchema.builder("integerValue")
            .field("value", FieldType.INTEGER)
            .build();

    private final ObjectManager objectManager;
    private final DriverFactory driverFactory;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "ispf-driver-runtime");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, ActiveDriver> activeDrivers = new ConcurrentHashMap<>();

    public DriverRuntimeService(
            ObjectManager objectManager,
            DriverFactory driverFactory,
            ObjectMapper objectMapper,
            Environment environment
    ) {
        this.objectManager = objectManager;
        this.driverFactory = driverFactory;
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void startConfiguredDrivers() {
        for (PlatformObject node : objectManager.tree().all()) {
            if (node.type() != ObjectType.DEVICE) {
                continue;
            }
            String path = node.path();
            if (!shouldAutoStart(path)) {
                continue;
            }
            if (readBinding(path).isEmpty()) {
                if ("root.platform.devices.demo-sensor-01".equals(path)) {
                    configure(path, DriverBinding.virtualDemo());
                } else {
                    continue;
                }
            }
            try {
                start(path);
            } catch (Exception e) {
                log.warn("Failed to auto-start driver for {}: {}", path, e.getMessage());
            }
        }
    }

    @PreDestroy
    void shutdownDrivers() {
        for (String devicePath : new ArrayList<>(activeDrivers.keySet())) {
            ActiveDriver active = activeDrivers.remove(devicePath);
            if (active == null) {
                continue;
            }
            active.future().cancel(false);
            try {
                active.driver().disconnect();
            } catch (Exception ex) {
                log.debug("Driver disconnect during shutdown for {}: {}", devicePath, ex.getMessage());
            }
        }
        scheduler.shutdownNow();
    }

    private boolean shouldAutoStart(String devicePath) {
        if (isLabTrainingDevice(devicePath)) {
            return false;
        }
        if (environment.acceptsProfiles(Profiles.of("test"))
                && "root.platform.devices.snmp-localhost".equals(devicePath)) {
            return false;
        }
        return true;
    }

    private static boolean isLabTrainingDevice(String devicePath) {
        return LabTrainingBundleLayouts.LAB_DEVICE_A.equals(devicePath)
                || LabTrainingBundleLayouts.LAB_DEVICE_B.equals(devicePath);
    }

    public Optional<DriverRuntimeStatus> status(String devicePath) {
        ActiveDriver active = activeDrivers.get(devicePath);
        if (active == null) {
            return readBinding(devicePath).map(binding -> new DriverRuntimeStatus(
                    devicePath,
                    binding.driverId(),
                    "STOPPED",
                    false,
                    binding.pollIntervalMs(),
                    null
            ));
        }
        return Optional.of(new DriverRuntimeStatus(
                devicePath,
                active.binding().driverId(),
                readStatusVariable(devicePath).orElse("RUNNING"),
                active.driver().isConnected(),
                active.binding().pollIntervalMs(),
                active.lastError()
        ));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DriverRuntimeStatus start(String devicePath) {
        stop(devicePath);
        PlatformObject device = objectManager.require(devicePath);
        if (device.type() != ObjectType.DEVICE) {
            throw new IllegalArgumentException("Drivers attach only to DEVICE objects: " + devicePath);
        }

        DriverBinding binding = readBinding(devicePath).orElse(DriverBinding.virtualDemo());
        DeviceDriver driver = driverFactory.create(binding.driverId());
        ServerDriverObject driverObject = new ServerDriverObject(
                device,
                binding.configuration(),
                update -> {
                    if (update.system()) {
                        objectManager.setSystemVariableValue(update.path(), update.variableName(), update.value());
                    } else {
                        objectManager.setDriverTelemetryValue(update.path(), update.variableName(), update.value());
                    }
                },
                entry -> log.info("[driver:{}] {} {}", devicePath, entry.level(), entry.message())
        );

        driver.initialize(driverObject);
        try {
            driver.connect();
        } catch (DriverException e) {
            setStatus(devicePath, "ERROR");
            throw new IllegalStateException("Driver connect failed: " + e.getMessage(), e);
        }

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> poll(devicePath),
                0,
                binding.pollIntervalMs(),
                TimeUnit.MILLISECONDS
        );
        activeDrivers.put(devicePath, new ActiveDriver(driver, binding, future, null));
        poll(devicePath);
        setStatus(devicePath, "RUNNING");
        return status(devicePath).orElseThrow();
    }

    public DriverRuntimeStatus stop(String devicePath) {
        ActiveDriver active = activeDrivers.remove(devicePath);
        DriverBinding binding = readBinding(devicePath).orElse(DriverBinding.virtualDemo());
        if (active != null) {
            active.future().cancel(false);
            active.driver().disconnect();
        }
        setStatus(devicePath, "STOPPED");
        return new DriverRuntimeStatus(
                devicePath,
                binding.driverId(),
                "STOPPED",
                false,
                binding.pollIntervalMs(),
                null
        );
    }

    public void stopIfRunning(String devicePath) {
        if (activeDrivers.containsKey(devicePath)) {
            stop(devicePath);
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DriverRuntimeStatus configure(String devicePath, DriverBinding binding) {
        objectManager.setVariableValue(
                devicePath,
                "driverId",
                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", binding.driverId()))
        );
        objectManager.setVariableValue(
                devicePath,
                "driverPollIntervalMs",
                DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", binding.pollIntervalMs()))
        );
        try {
            objectManager.setVariableValue(
                    devicePath,
                    "driverConfigJson",
                    DataRecord.single(
                            STRING_VALUE_SCHEMA,
                            Map.of("value", objectMapper.writeValueAsString(binding.configuration()))
                    )
            );
            objectManager.setVariableValue(
                    devicePath,
                    "driverPointMappingsJson",
                    DataRecord.single(
                            STRING_VALUE_SCHEMA,
                            Map.of("value", objectMapper.writeValueAsString(binding.pointMappings()))
                    )
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist driver configuration", e);
        }
        return status(devicePath).orElseThrow();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DriverRuntimeStatus pollNow(String devicePath) {
        poll(devicePath);
        return status(devicePath).orElseThrow();
    }

    private void poll(String devicePath) {
        ActiveDriver active = activeDrivers.get(devicePath);
        if (active == null) {
            return;
        }
        try {
            active.driver().readPoints(active.binding().pointMappings());
            activeDrivers.put(devicePath, new ActiveDriver(
                    active.driver(),
                    active.binding(),
                    active.future(),
                    null
            ));
            setStatus(devicePath, "RUNNING");
        } catch (Exception e) {
            activeDrivers.put(devicePath, new ActiveDriver(
                    active.driver(),
                    active.binding(),
                    active.future(),
                    e.getMessage()
            ));
            setStatus(devicePath, "ERROR");
            log.warn("Driver poll failed for {}: {}", devicePath, e.getMessage());
        }
    }

    private Optional<DriverBinding> readBinding(String devicePath) {
        PlatformObject device = objectManager.tree().findByPath(devicePath).orElse(null);
        if (device == null) {
            return Optional.empty();
        }
        String driverId = stringValue(device, "driverId").orElse("");
        if (driverId.isBlank()) {
            return Optional.empty();
        }
        int pollInterval = intValue(device, "driverPollIntervalMs").orElse(2000);
        String configJson = stringValue(device, "driverConfigJson").orElse("{}");
        String mappingsJson = stringValue(device, "driverPointMappingsJson").orElse("{}");
        return Optional.of(DriverBinding.parse(driverId, pollInterval, configJson, mappingsJson, objectMapper));
    }

    private Optional<String> readStatusVariable(String devicePath) {
        return objectManager.tree().findByPath(devicePath).flatMap(node -> stringValue(node, "driverStatus"));
    }

    private void setStatus(String devicePath, String status) {
        objectManager.setSystemVariableValue(
                devicePath,
                "driverStatus",
                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", status))
        );
    }

    private static Optional<String> stringValue(PlatformObject node, String variableName) {
        return node.getVariable(variableName)
                .flatMap(com.ispf.core.object.Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString);
    }

    private static Optional<Integer> intValue(PlatformObject node, String variableName) {
        return node.getVariable(variableName)
                .flatMap(com.ispf.core.object.Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(value -> ((Number) value).intValue());
    }

    private record ActiveDriver(
            DeviceDriver driver,
            DriverBinding binding,
            ScheduledFuture<?> future,
            String lastError
    ) {
    }

    public record DriverRuntimeStatus(
            String devicePath,
            String driverId,
            String status,
            boolean connected,
            int pollIntervalMs,
            String lastError
    ) {
    }

    public Map<String, Object> runtimeMetrics() {
        long active = activeDrivers.size();
        long connected = activeDrivers.values().stream()
                .filter(entry -> entry.driver().isConnected())
                .count();
        long withError = activeDrivers.values().stream()
                .filter(entry -> entry.lastError() != null)
                .count();
        long devices = objectManager.tree().all().stream()
                .filter(node -> node.type() == ObjectType.DEVICE)
                .count();

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("deviceObjects", devices);
        metrics.put("activeDrivers", active);
        metrics.put("connectedDrivers", connected);
        metrics.put("driversWithError", withError);
        metrics.put("stoppedDrivers", Math.max(0, devices - active));
        return metrics;
    }
}
