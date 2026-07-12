package com.ispf.server.driver;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.config.RuntimeTelemetryProperties;
import com.ispf.server.object.ObjectManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves per-device telemetry publish policy from driver binding variables,
 * with optional per-variable overrides on {@link Variable}.
 */
@Service
public class DeviceTelemetryPolicyService {

    private static final long CACHE_TTL_MS = 1_000;

    private final ObjectManager objectManager;
    private final ObjectMapper objectMapper;
    private final RuntimeTelemetryProperties globalProperties;
    private final ConcurrentHashMap<String, CachedPolicy> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedVariableMode> variableModeCache = new ConcurrentHashMap<>();

    public DeviceTelemetryPolicyService(
            @Lazy ObjectManager objectManager,
            ObjectMapper objectMapper,
            RuntimeTelemetryProperties globalProperties
    ) {
        this.objectManager = objectManager;
        this.objectMapper = objectMapper;
        this.globalProperties = globalProperties;
    }

    /** Device-level default from driver binding (or FULL when unbound). */
    public TelemetryPublishMode publishMode(String devicePath) {
        return policyFor(devicePath).publishMode();
    }

    /**
     * Effective publish mode for a variable: per-variable override, else bound device default, else FULL.
     */
    public TelemetryPublishMode publishMode(String objectPath, String variableName) {
        long now = System.currentTimeMillis();
        String cacheKey = objectPath + "|" + variableName;
        CachedVariableMode cached = variableModeCache.get(cacheKey);
        if (cached != null && now - cached.loadedAtMs() < CACHE_TTL_MS) {
            return cached.mode();
        }
        TelemetryPublishMode resolved = resolvePublishMode(objectPath, variableName);
        variableModeCache.put(cacheKey, new CachedVariableMode(resolved, now));
        return resolved;
    }

    public long coalesceMs(String devicePath) {
        return policyFor(devicePath).coalesceMs();
    }

    public boolean automationEligible(String devicePath) {
        return policyFor(devicePath).automationEligible();
    }

    public boolean automationEligible(String objectPath, String variableName) {
        return publishMode(objectPath, variableName).automationEligible();
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
            String prefix = devicePath + "|";
            variableModeCache.keySet().removeIf(key -> key.startsWith(prefix));
        }
    }

    public void invalidateVariable(String objectPath, String variableName) {
        if (objectPath != null && !objectPath.isBlank() && variableName != null && !variableName.isBlank()) {
            variableModeCache.remove(objectPath + "|" + variableName);
        }
    }

    private TelemetryPublishMode resolvePublishMode(String objectPath, String variableName) {
        Optional<String> override = readVariablePublishModeOverride(objectPath, variableName);
        if (override.isPresent()) {
            return TelemetryPublishMode.parse(override.get());
        }
        return readBinding(objectPath)
                .map(DriverBinding::telemetryPublishMode)
                .orElse(TelemetryPublishMode.FULL);
    }

    private Optional<String> readVariablePublishModeOverride(String objectPath, String variableName) {
        return objectManager.tree().findByPath(objectPath)
                .flatMap(node -> node.getVariable(variableName))
                .flatMap(Variable::telemetryPublishModeOverride);
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
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString);
    }

    private static Optional<Integer> intValue(PlatformObject node, String variableName) {
        return node.getVariable(variableName)
                .flatMap(Variable::value)
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

    private record CachedVariableMode(TelemetryPublishMode mode, long loadedAtMs) {}
}
