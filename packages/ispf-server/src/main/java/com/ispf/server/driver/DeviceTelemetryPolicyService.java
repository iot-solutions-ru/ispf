package com.ispf.server.driver;

import com.ispf.core.object.PlatformObject;
import com.ispf.server.config.RuntimeTelemetryProperties;
import com.ispf.server.object.ObjectManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves per-device telemetry publish policy from driver binding variables.
 */
@Service
public class DeviceTelemetryPolicyService {

    private static final long CACHE_TTL_MS = 1_000;

    private final ObjectManager objectManager;
    private final ObjectMapper objectMapper;
    private final RuntimeTelemetryProperties globalProperties;
    private final ConcurrentHashMap<String, CachedPolicy> cache = new ConcurrentHashMap<>();

    public DeviceTelemetryPolicyService(
            @Lazy ObjectManager objectManager,
            ObjectMapper objectMapper,
            RuntimeTelemetryProperties globalProperties
    ) {
        this.objectManager = objectManager;
        this.objectMapper = objectMapper;
        this.globalProperties = globalProperties;
    }

    public TelemetryPublishMode publishMode(String devicePath) {
        return policyFor(devicePath).publishMode();
    }

    public long coalesceMs(String devicePath) {
        return policyFor(devicePath).coalesceMs();
    }

    public boolean automationEligible(String devicePath) {
        return policyFor(devicePath).automationEligible();
    }

    /** When true, MQTT ingress uses per-topic coalesce lanes + parallel thread-pool dispatch. */
    public boolean ingressTopicLanes(String devicePath) {
        return policyFor(devicePath).ingressTopicLanes();
    }

    /** When true, coalesce lanes are keyed by ingress payload (same topic, many devices). */
    public boolean ingressPayloadLanes(String devicePath) {
        return policyFor(devicePath).ingressPayloadLanes();
    }

    public boolean parallelIngressDispatch(String devicePath) {
        CachedPolicy policy = policyFor(devicePath);
        return policy.ingressTopicLanes() || policy.ingressPayloadLanes();
    }

    private static boolean parseBooleanFlag(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        return Boolean.parseBoolean(raw.trim());
    }

    public Optional<DriverBinding> bindingFor(String devicePath) {
        return readBinding(devicePath);
    }

    public void invalidateCache(String devicePath) {
        if (devicePath != null && !devicePath.isBlank()) {
            cache.remove(devicePath);
        }
    }

    private CachedPolicy policyFor(String devicePath) {
        long now = System.currentTimeMillis();
        CachedPolicy cached = cache.get(devicePath);
        if (cached != null && now - cached.loadedAtMs() < CACHE_TTL_MS) {
            return cached;
        }
        CachedPolicy loaded = readBinding(devicePath)
                .map(binding -> policyFromBinding(binding, now))
                .orElseGet(() -> defaultPolicy(now));
        cache.put(devicePath, loaded);
        return loaded;
    }

    private CachedPolicy policyFromBinding(DriverBinding binding, long loadedAtMs) {
        TelemetryPublishMode publishMode = binding.telemetryPublishMode();
        return new CachedPolicy(
                publishMode,
                binding.effectiveCoalesceMs(globalProperties.getCoalesceMs()),
                publishMode.automationEligible(),
                parseBooleanFlag(binding.configuration().get("ingressTopicLanes")),
                parseBooleanFlag(binding.configuration().get("ingressPayloadLanes")),
                loadedAtMs
        );
    }

    private CachedPolicy defaultPolicy(long loadedAtMs) {
        return new CachedPolicy(
                TelemetryPublishMode.FULL,
                globalProperties.getCoalesceMs(),
                true,
                false,
                false,
                loadedAtMs
        );
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

    private record CachedPolicy(
            TelemetryPublishMode publishMode,
            long coalesceMs,
            boolean automationEligible,
            boolean ingressTopicLanes,
            boolean ingressPayloadLanes,
            long loadedAtMs
    ) {}
}
