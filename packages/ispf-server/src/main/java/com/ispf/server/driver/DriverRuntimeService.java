package com.ispf.server.driver;

import tools.jackson.databind.ObjectMapper;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.ObjectType;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverDiscovery;
import com.ispf.driver.DriverException;
import com.ispf.driver.ingress.DriverIngress;
import com.ispf.server.bootstrap.LabTrainingBundleLayouts;
import com.ispf.driver.ingress.DriverIngressBuffer;
import com.ispf.server.concurrent.ElasticScheduledPool;
import com.ispf.server.concurrent.ElasticWorkerLauncher;
import com.ispf.server.config.DriverPackProperties;
import com.ispf.server.config.RuntimeTelemetryProperties;
import com.ispf.server.driver.TelemetryPublishMode;
import com.ispf.server.object.ObjectManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final DeviceTelemetryPolicyService telemetryPolicyService;
    private final DriverPackProperties driverPackProperties;
    private final RuntimeTelemetryProperties runtimeTelemetryProperties;
    private final ObjectProvider<DriverRuntimeService> self;
    private final DriverOwnershipService ownershipService;
    private ElasticScheduledPool schedulerPool;
    private ScheduledThreadPoolExecutor scheduler;
    private ElasticWorkerLauncher ioWorkers;
    private final ConcurrentLinkedQueue<Runnable> ioPending = new ConcurrentLinkedQueue<>();
    private final AtomicInteger ioPendingCount = new AtomicInteger();
    private final Map<String, ActiveDriver> activeDrivers = new ConcurrentHashMap<>();

    public DriverRuntimeService(
            ObjectManager objectManager,
            DriverFactory driverFactory,
            ObjectMapper objectMapper,
            Environment environment,
            DeviceTelemetryPolicyService telemetryPolicyService,
            DriverPackProperties driverPackProperties,
            RuntimeTelemetryProperties runtimeTelemetryProperties,
            ObjectProvider<DriverRuntimeService> self,
            DriverOwnershipService ownershipService
    ) {
        this.objectManager = objectManager;
        this.driverFactory = driverFactory;
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.telemetryPolicyService = telemetryPolicyService;
        this.driverPackProperties = driverPackProperties;
        this.runtimeTelemetryProperties = runtimeTelemetryProperties;
        this.self = self;
        this.ownershipService = ownershipService;
    }

    @PostConstruct
    void startExecutors() {
        schedulerPool = new ElasticScheduledPool(
                driverPackProperties.resolvedSchedulerElastic(),
                () -> activeDrivers.size(),
                "ispf-driver-runtime"
        );
        scheduler = schedulerPool.start();
        ioWorkers = new ElasticWorkerLauncher(
                driverPackProperties.resolvedIoElastic(),
                ioPendingCount::get,
                "ispf-driver-io",
                this::drainOneIoPoll
        );
        ioWorkers.start();
    }

    private boolean drainOneIoPoll() {
        Runnable task = ioPending.poll();
        if (task == null) {
            return false;
        }
        ioPendingCount.decrementAndGet();
        task.run();
        return true;
    }

    private void signalSchedulerLoad() {
        if (schedulerPool != null) {
            schedulerPool.signalLoad();
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void startConfiguredDrivers() {
        if (!driverPackProperties.isAutoStartOnBoot()) {
            log.info("Driver auto-start on boot disabled (ispf.driver.auto-start-on-boot=false)");
            return;
        }
        for (PlatformObject node : objectManager.tree().childrenOf("root.platform.devices")) {
            if (node.type() != ObjectType.DEVICE) {
                continue;
            }
            String path = node.path();
            if (!shouldAutoStart(path)) {
                continue;
            }
            if (readBinding(path).isEmpty()) {
                if ("root.platform.devices.demo-sensor-01".equals(path)) {
                    self.getObject().configure(path, DriverBinding.virtualDemo());
                } else {
                    continue;
                }
            }
            try {
                self.getObject().start(path);
            } catch (Exception e) {
                log.warn("Failed to auto-start driver for {}: {}", path, e.getMessage());
            }
        }
    }

    @PreDestroy
    void shutdownDrivers() {
        for (String devicePath : new ArrayList<>(activeDrivers.keySet())) {
            stopLocal(devicePath, true);
        }
        if (schedulerPool != null) {
            schedulerPool.close();
        }
        if (ioWorkers != null) {
            ioWorkers.close();
        }
        Runnable task;
        while ((task = ioPending.poll()) != null) {
            ioPendingCount.decrementAndGet();
            task.run();
        }
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
            return readBinding(devicePath).map(binding -> statusOf(
                    devicePath,
                    binding,
                    "STOPPED",
                    false,
                    null
            ));
        }
        return Optional.of(statusOf(
                devicePath,
                active.binding(),
                readStatusVariable(devicePath).orElse("RUNNING"),
                active.driver().isConnected(),
                active.lastError()
        ));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DriverRuntimeStatus start(String devicePath) {
        stopLocal(devicePath, true);
        if (!ownershipService.tryAcquire(devicePath)) {
            setStatus(devicePath, "STANDBY");
            String owner = ownershipService.findOwner(devicePath).orElse("unknown");
            throw new IllegalStateException(
                    "Driver owned by replica " + owner + "; this replica is " + ownershipService.instanceId()
            );
        }
        return doStart(devicePath);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DriverRuntimeStatus startHeld(String devicePath) {
        if (activeDrivers.containsKey(devicePath)) {
            return status(devicePath).orElseThrow();
        }
        if (ownershipService.isEnabled() && !ownershipService.holdsLock(devicePath)) {
            throw new IllegalStateException("Not driver lock holder for: " + devicePath);
        }
        return doStart(devicePath);
    }

    private DriverRuntimeStatus doStart(String devicePath) {
        PlatformObject device = objectManager.require(devicePath);
        if (device.type() != ObjectType.DEVICE) {
            throw new IllegalArgumentException("Drivers attach only to DEVICE objects: " + devicePath);
        }

        DriverBinding binding = readBinding(devicePath).orElse(DriverBinding.virtualDemo());
        DeviceDriver driver = driverFactory.create(binding.driverId());
        Consumer<ServerDriverObject.VariableUpdate> variableUpdater = update -> {
            if (update.system()) {
                objectManager.setSystemVariableValue(update.path(), update.variableName(), update.value());
            } else {
                objectManager.setDriverTelemetryValue(
                        update.path(), update.variableName(), update.value(), update.observedAt()
                );
            }
        };
        DriverIngressBuffer<String, ServerDriverObject.VariableUpdate> ingressBuffer = null;
        if (driverPackProperties.isIngressBufferEnabled() && !usesDirectIngress(devicePath)) {
            String threadPrefix = "driver-ingress-" + devicePath.substring(Math.max(0, devicePath.length() - 24));
            ingressBuffer = new DriverIngressBuffer<>(
                    driverPackProperties.resolvedIngressBufferElastic(),
                    driverPackProperties.getIngressBufferCapacity(),
                    (name, update) -> variableUpdater.accept(update),
                    threadPrefix,
                    false
            );
        }
        ServerDriverObject driverObject = new ServerDriverObject(
                device,
                effectiveDriverConfiguration(binding),
                variableUpdater,
                entry -> log.info("[driver:{}] {} {}", devicePath, entry.level(), entry.message()),
                ingressBuffer
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
        boolean connected = driver.isConnected();
        activeDrivers.put(devicePath, new ActiveDriver(driver, binding, future, null, connected, driverObject));
        signalSchedulerLoad();
        poll(devicePath);
        setStatus(devicePath, "RUNNING");
        return status(devicePath).orElseThrow();
    }

    public DriverRuntimeStatus stop(String devicePath) {
        return stopLocal(devicePath, true);
    }

    public DriverRuntimeStatus stopLocal(String devicePath, boolean releaseOwnership) {
        ActiveDriver active = activeDrivers.remove(devicePath);
        signalSchedulerLoad();
        DriverBinding binding = readBinding(devicePath).orElse(DriverBinding.virtualDemo());
        if (active != null) {
            active.future().cancel(false);
            active.driver().disconnect();
            active.driverObject().shutdown();
        }
        if (releaseOwnership) {
            ownershipService.release(devicePath);
        }
        setStatus(devicePath, "STOPPED");
        return statusOf(
                devicePath,
                binding,
                "STOPPED",
                false,
                null
        );
    }

    public void stopIfRunning(String devicePath) {
        if (activeDrivers.containsKey(devicePath)) {
            stopLocal(devicePath, true);
        }
    }

    public boolean isActiveLocally(String devicePath) {
        return activeDrivers.containsKey(devicePath);
    }

    public List<String> activeDevicePaths() {
        return List.copyOf(activeDrivers.keySet());
    }

    @Transactional
    public DriverRuntimeStatus configure(String devicePath, DriverBinding binding) {
        Optional<DriverBinding> existing = readBinding(devicePath);
        // #region agent log
        agentLog("DriverRuntimeService.java:configure", "configure invoked", "H4", Map.of(
                "devicePath", devicePath,
                "requestedDriverId", binding.driverId(),
                "existingDriverId", existing.map(DriverBinding::driverId).orElse("(none)"),
                "willReject", existing.isPresent() && !existing.get().driverId().equals(binding.driverId())
        ));
        // #endregion
        if (existing.isPresent() && !existing.get().driverId().equals(binding.driverId())) {
            throw new IllegalStateException(
                    "Driver already configured as " + existing.get().driverId()
                            + "; cannot switch to " + binding.driverId()
            );
        }
        persistDriverBinding(devicePath, binding);
        return status(devicePath).orElseThrow();
    }

    private void persistDriverBinding(String devicePath, DriverBinding binding) {
        telemetryPolicyService.invalidateCache(devicePath);
        objectManager.setSystemVariableValue(
                devicePath,
                "driverId",
                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", binding.driverId()))
        );
        objectManager.setSystemVariableValue(
                devicePath,
                "driverPollIntervalMs",
                DataRecord.single(INTEGER_VALUE_SCHEMA, Map.of("value", binding.pollIntervalMs()))
        );
        try {
            objectManager.setSystemVariableValue(
                    devicePath,
                    "driverConfigJson",
                    DataRecord.single(
                            STRING_VALUE_SCHEMA,
                            Map.of("value", objectMapper.writeValueAsString(binding.configurationWithPolicy()))
                    )
            );
            objectManager.setSystemVariableValue(
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
        telemetryPolicyService.invalidateCache(devicePath);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DriverRuntimeStatus pollNow(String devicePath) {
        return pollNow(devicePath, null);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DriverRuntimeStatus pollNow(String devicePath, String pointId) {
        ActiveDriver active = activeDrivers.get(devicePath);
        if (active == null) {
            return status(devicePath).orElseThrow();
        }
        pollOnIoThread(devicePath, active, pointId);
        active.driverObject().flushIngress();
        return status(devicePath).orElseThrow();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DriverRuntimeStatus writePoint(String devicePath, String pointId, DataRecord value) {
        if (pointId == null || pointId.isBlank()) {
            throw new IllegalArgumentException("pointId is required");
        }
        if (value == null) {
            throw new IllegalArgumentException("value is required");
        }
        ActiveDriver active = activeDrivers.get(devicePath);
        if (active == null) {
            throw new IllegalStateException("Driver is not running for: " + devicePath);
        }
        if (!active.binding().pointMappings().containsKey(pointId)) {
            throw new IllegalArgumentException("Unknown point mapping: " + pointId);
        }
        try {
            active.driver().writePoint(pointId, value);
            boolean connected = active.driver().isConnected();
            activeDrivers.put(devicePath, new ActiveDriver(
                    active.driver(),
                    active.binding(),
                    active.future(),
                    null,
                    connected,
                    active.driverObject()
            ));
            setStatus(devicePath, "RUNNING");
        } catch (DriverException e) {
            activeDrivers.put(devicePath, new ActiveDriver(
                    active.driver(),
                    active.binding(),
                    active.future(),
                    e.getMessage(),
                    active.lastKnownConnected(),
                    active.driverObject()
            ));
            setStatus(devicePath, "ERROR");
            throw new IllegalStateException("Driver write failed: " + e.getMessage(), e);
        }
        active.driverObject().flushIngress();
        return status(devicePath).orElseThrow();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public java.util.List<DriverDiscovery.Node> browseDriverChildren(String devicePath, String parentNodeId) {
        ActiveDriver active = activeDrivers.get(devicePath);
        if (active != null && active.driver() instanceof DriverDiscovery discovery) {
            try {
                return discovery.browseChildren(parentNodeId);
            } catch (DriverException e) {
                throw new IllegalStateException("Driver browse failed: " + e.getMessage(), e);
            }
        }
        DriverBinding binding = readBinding(devicePath).orElseThrow(
                () -> new IllegalArgumentException("No driver binding for: " + devicePath)
        );
        DeviceDriver driver = driverFactory.create(binding.driverId());
        if (!(driver instanceof DriverDiscovery discovery)) {
            throw new IllegalArgumentException("Driver does not support browse: " + binding.driverId());
        }
        PlatformObject device = objectManager.require(devicePath);
        ServerDriverObject driverObject = new ServerDriverObject(
                device,
                binding.configuration(),
                update -> {
                },
                entry -> log.debug("[driver:{}] {} {}", devicePath, entry.level(), entry.message())
        );
        driver.initialize(driverObject);
        try {
            driver.connect();
            return discovery.browseChildren(parentNodeId);
        } catch (DriverException e) {
            throw new IllegalStateException("Driver browse failed: " + e.getMessage(), e);
        } finally {
            driver.disconnect();
        }
    }

    private void poll(String devicePath) {
        poll(devicePath, null);
    }

    private void poll(String devicePath, String pointId) {
        ActiveDriver active = activeDrivers.get(devicePath);
        if (active == null) {
            return;
        }
        if (driverPackProperties.isAsyncPollEnabled()) {
            ioPendingCount.incrementAndGet();
            ioPending.offer(() -> pollOnIoThread(devicePath, active, pointId));
            ioWorkers.signalWork();
            return;
        }
        pollOnIoThread(devicePath, active, pointId);
    }

    private void pollOnIoThread(String devicePath, ActiveDriver active, String pointId) {
        try {
            Map<String, String> mappings = active.binding().pointMappings();
            if (pointId != null && !pointId.isBlank()) {
                if (!mappings.containsKey(pointId)) {
                    throw new IllegalArgumentException("Unknown point mapping: " + pointId);
                }
                mappings = Map.of(pointId, mappings.get(pointId));
            }
            active.driver().readPoints(mappings);
            boolean connected = active.driver().isConnected();
            ActiveDriver next = new ActiveDriver(
                    active.driver(),
                    active.binding(),
                    active.future(),
                    null,
                    connected,
                    active.driverObject()
            );
            activeDrivers.put(devicePath, next);
            notifyConnectionIfChanged(devicePath, active, next);
            setStatus(devicePath, "RUNNING");
        } catch (Exception e) {
            activeDrivers.put(devicePath, new ActiveDriver(
                    active.driver(),
                    active.binding(),
                    active.future(),
                    e.getMessage(),
                    active.lastKnownConnected(),
                    active.driverObject()
            ));
            setStatus(devicePath, "ERROR");
            log.warn("Driver poll failed for {}: {}", devicePath, e.getMessage());
        }
    }

    public Optional<DriverBinding> readBinding(String devicePath) {
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
        boolean unchanged = readStatusVariable(devicePath).map(status::equals).orElse(false);
        objectManager.setRuntimeVariableValue(
                devicePath,
                "driverStatus",
                DataRecord.single(STRING_VALUE_SCHEMA, Map.of("value", status)),
                !unchanged
        );
    }

    private void notifyConnectionIfChanged(String devicePath, ActiveDriver previous, ActiveDriver next) {
        if (previous.lastKnownConnected() == next.lastKnownConnected()) {
            return;
        }
        objectManager.publishDriverRuntimeChanged(devicePath);
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
            String lastError,
            boolean lastKnownConnected,
            ServerDriverObject driverObject
    ) {
    }

    public record DriverRuntimeStatus(
            String devicePath,
            String driverId,
            String status,
            boolean connected,
            int pollIntervalMs,
            String lastError,
            String telemetryPublishMode,
            int telemetryCoalesceMs
    ) {
    }

    private static DriverRuntimeStatus statusOf(
            String devicePath,
            DriverBinding binding,
            String status,
            boolean connected,
            String lastError
    ) {
        return new DriverRuntimeStatus(
                devicePath,
                binding.driverId(),
                status,
                connected,
                binding.pollIntervalMs(),
                lastError,
                binding.telemetryPublishMode().name(),
                binding.telemetryCoalesceMs()
        );
    }

    public Map<String, Object> runtimeMetrics() {
        long active = activeDrivers.size();
        long connected = activeDrivers.values().stream()
                .filter(entry -> entry.driver().isConnected())
                .count();
        long withError = activeDrivers.values().stream()
                .filter(entry -> entry.lastError() != null)
                .count();
        long devices = objectManager.tree().childrenOf("root.platform.devices").stream()
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

    public Map<String, Object> driverDiagnosticsSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("ioPollQueueSize", ioPendingCount.get());
        if (scheduler != null) {
            snapshot.put("schedulerPoolSize", scheduler.getPoolSize());
            snapshot.put("schedulerActiveCount", scheduler.getActiveCount());
        }
        if (ioWorkers != null) {
            snapshot.put("ioWorkersActive", ioWorkers.activeWorkers().get());
        }
        List<Map<String, Object>> drivers = new ArrayList<>();
        for (Map.Entry<String, ActiveDriver> entry : activeDrivers.entrySet()) {
            String devicePath = entry.getKey();
            ActiveDriver active = entry.getValue();
            DriverBinding binding = active.binding();
            Map<String, Object> driver = new LinkedHashMap<>();
            driver.put("devicePath", devicePath);
            driver.put("driverId", binding.driverId());
            driver.put("pollIntervalMs", binding.pollIntervalMs());
            driver.put("pointMappingCount", binding.pointMappings().size());
            driver.put("timeoutMs", driverTimeoutMs(binding));
            driver.put("telemetryPublishMode", binding.telemetryPublishMode().name());
            driver.put("connected", active.driver().isConnected());
            driver.put("lastError", active.lastError());
            driver.putAll(active.driverObject().ingressStats());
            driver.put("pressureScore", driverPressureScore(binding, active));
            drivers.add(driver);
        }
        drivers.sort((left, right) -> Integer.compare(
                ((Number) right.getOrDefault("pressureScore", 0)).intValue(),
                ((Number) left.getOrDefault("pressureScore", 0)).intValue()
        ));
        snapshot.put("drivers", drivers);
        return snapshot;
    }

    private static int driverPressureScore(DriverBinding binding, ActiveDriver active) {
        int score = 0;
        Map<String, Object> ingress = active.driverObject().ingressStats();
        if (Boolean.TRUE.equals(ingress.get("ingressEnabled"))) {
            score += ((Number) ingress.getOrDefault("ingressPending", 0)).intValue() * 10;
            score += ((Number) ingress.getOrDefault("ingressCoalesced", 0)).intValue();
            score += ((Number) ingress.getOrDefault("ingressEvicted", 0)).intValue() * 5;
        }
        int pointCount = binding.pointMappings().size();
        if (pointCount >= 10) {
            score += Math.min(20, pointCount / 2);
        }
        long timeoutMs = driverTimeoutMs(binding);
        if (pointCount > 0 && binding.pollIntervalMs() > 0 && timeoutMs > 0) {
            long worstCasePollMs = pointCount * timeoutMs;
            if (worstCasePollMs > binding.pollIntervalMs()) {
                score += Math.min(60, (int) (worstCasePollMs / binding.pollIntervalMs()));
            }
        }
        if (binding.pollIntervalMs() > 0 && binding.pollIntervalMs() < 1000) {
            score += 50;
        }
        if (active.lastError() != null) {
            score += 30;
        }
        if (!active.driver().isConnected()) {
            score += 10;
        }
        return score;
    }

    private static long driverTimeoutMs(DriverBinding binding) {
        String raw = binding.configuration().get("timeoutMs");
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    /**
     * High-rate ingress modes skip the server driver ingress buffer (L1) so MQTT L0 drain is not stacked
     * with platform ingress coalescing.
     */
    private boolean usesDirectIngress(String devicePath) {
        TelemetryPublishMode mode = telemetryPolicyService.publishMode(devicePath);
        if (mode == TelemetryPublishMode.EVENT_JOURNAL_ONLY) {
            return true;
        }
        return runtimeTelemetryProperties.isFastHistorianPath()
                && mode == TelemetryPublishMode.TELEMETRY_ONLY;
    }

    /** Debug-only helper for agent instrumentation session c91425. */
    public java.util.Optional<String> debugReadDriverId(String devicePath) {
        return readBinding(devicePath).map(DriverBinding::driverId);
    }

    private Map<String, String> effectiveDriverConfiguration(DriverBinding binding) {
        Map<String, String> configuration = new LinkedHashMap<>(binding.configuration());
        if (binding.telemetryPublishMode() != TelemetryPublishMode.FULL) {
            configuration.put("telemetryPublishMode", binding.telemetryPublishMode().name());
        }
        if (binding.telemetryPublishMode() == TelemetryPublishMode.EVENT_JOURNAL_ONLY) {
            configuration.put(DriverIngress.INGRESS_COALESCE_ENABLED, "false");
        }
        if (!"mqtt".equalsIgnoreCase(binding.driverId())) {
            return configuration;
        }
        configuration.putIfAbsent(
                DriverIngress.CALLBACK_THREADS,
                String.valueOf(driverPackProperties.getMqttCallbackThreads())
        );
        configuration.putIfAbsent(
                DriverIngress.CALLBACK_ELASTIC_ENABLED,
                String.valueOf(driverPackProperties.isMqttCallbackElasticEnabled())
        );
        configuration.putIfAbsent(
                DriverIngress.CALLBACK_THREADS_MIN,
                String.valueOf(driverPackProperties.resolvedMqttCallbackThreadsMin())
        );
        configuration.putIfAbsent(
                DriverIngress.CALLBACK_THREADS_MAX,
                String.valueOf(driverPackProperties.resolvedMqttCallbackThreadsMax())
        );
        configuration.putIfAbsent(
                DriverIngress.CALLBACK_SCALE_UP_QUEUE_THRESHOLD,
                String.valueOf(driverPackProperties.getMqttCallbackScaleUpQueueThreshold())
        );
        configuration.putIfAbsent(
                DriverIngress.CALLBACK_SCALE_DOWN_STEPS,
                String.valueOf(driverPackProperties.getMqttCallbackScaleDownSteps())
        );
        configuration.putIfAbsent(
                DriverIngress.CALLBACK_SCALE_CHECK_INTERVAL_MS,
                String.valueOf(driverPackProperties.getMqttCallbackScaleCheckIntervalMs())
        );
        configuration.putIfAbsent(
                DriverIngress.CALLBACK_QUEUE_CAPACITY,
                String.valueOf(driverPackProperties.getMqttCallbackQueueCapacity())
        );
        configuration.putIfAbsent(
                DriverIngress.INGRESS_COALESCE_ENABLED,
                String.valueOf(driverPackProperties.isMqttIngressCoalesceEnabled())
        );
        return configuration;
    }

    // #region agent log
    private static void agentLog(String location, String message, String hypothesisId, Map<String, Object> data) {
        try {
            String json = "{\"sessionId\":\"c91425\",\"timestamp\":" + System.currentTimeMillis()
                    + ",\"location\":\"" + location + "\",\"message\":\"" + message + "\",\"hypothesisId\":\""
                    + hypothesisId + "\",\"data\":" + new ObjectMapper().writeValueAsString(data) + "}\n";
            for (String rel : new String[]{"debug-c91425.log", "../../debug-c91425.log", "../../../debug-c91425.log"}) {
                Path p = Path.of(rel);
                if (Files.exists(p.getParent() == null ? Path.of(".") : p.getParent()) || rel.equals("debug-c91425.log")) {
                    Files.writeString(p, json, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    break;
                }
            }
        } catch (Exception ignored) {
        }
    }
    // #endregion
}
