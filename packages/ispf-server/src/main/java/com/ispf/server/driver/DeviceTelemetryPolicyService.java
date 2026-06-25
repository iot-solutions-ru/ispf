package com.ispf.server.driver;

import com.ispf.core.object.PlatformObject;
import com.ispf.server.config.RuntimeTelemetryProperties;
import com.ispf.server.object.ObjectManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

/**
 * Resolves per-device telemetry publish policy from driver binding variables.
 */
@Service
public class DeviceTelemetryPolicyService {

    private final ObjectManager objectManager;
    private final ObjectMapper objectMapper;
    private final RuntimeTelemetryProperties globalProperties;

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
        return bindingFor(devicePath)
                .map(DriverBinding::telemetryPublishMode)
                .orElse(TelemetryPublishMode.FULL);
    }

    public long coalesceMs(String devicePath) {
        return bindingFor(devicePath)
                .map(binding -> binding.effectiveCoalesceMs(globalProperties.getCoalesceMs()))
                .orElse(globalProperties.getCoalesceMs());
    }

    public boolean automationEligible(String devicePath) {
        return publishMode(devicePath).automationEligible();
    }

    public Optional<DriverBinding> bindingFor(String devicePath) {
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
}
