package com.ispf.server.driver;

import com.ispf.driver.DriverMetadata;
import com.ispf.server.spi.DeviceObjectAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Embeds the device-driver variable schema (fixture blueprint) and configures driver defaults on DEVICE objects.
 */
@Service
public class DeviceProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(DeviceProvisioningService.class);

    private final DeviceObjectAccess objects;
    private final DriverCatalog driverCatalog;
    private final DriverRuntimeService driverRuntimeService;

    public DeviceProvisioningService(
            DeviceObjectAccess objects,
            DriverCatalog driverCatalog,
            DriverRuntimeService driverRuntimeService
    ) {
        this.objects = objects;
        this.driverCatalog = driverCatalog;
        this.driverRuntimeService = driverRuntimeService;
    }

    public void provisionDriver(String devicePath, String driverId, Integer pollIntervalMs, boolean autoStart) {
        provisionDriver(devicePath, driverId, pollIntervalMs, autoStart, null);
    }

    public void provisionDriver(
            String devicePath,
            String driverId,
            Integer pollIntervalMs,
            boolean autoStart,
            Map<String, String> configurationOverride
    ) {
        if (driverId == null || driverId.isBlank()) {
            return;
        }
        String resolvedDriverId = driverId.trim();
        DriverMetadata metadata = driverCatalog.list().stream()
                .filter(driver -> driver.id().equals(resolvedDriverId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unknown driverId: " + resolvedDriverId
                ));

        objects.ensureDeviceDriverStructure(devicePath);

        int interval = pollIntervalMs != null && pollIntervalMs > 0 ? pollIntervalMs : 5000;
        Map<String, String> configuration = new LinkedHashMap<>();
        if (metadata.configurationSchema() != null) {
            configuration.putAll(metadata.configurationSchema());
        }
        if (configurationOverride != null) {
            configurationOverride.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null) {
                    configuration.put(key.trim(), value);
                }
            });
        }

        DriverBinding binding = DriverBinding.of(resolvedDriverId, interval, configuration, Map.of());
        driverRuntimeService.configure(devicePath, binding);
        driverRuntimeService.setDriverAutoStart(devicePath, autoStart);
        if (autoStart) {
            try {
                driverRuntimeService.start(devicePath);
            } catch (IllegalStateException connectFailure) {
                if (connectFailure.getMessage() == null
                        || !connectFailure.getMessage().startsWith("Driver connect failed")) {
                    throw connectFailure;
                }
                log.warn("Created {} with driver {} left in error: {}",
                        devicePath, resolvedDriverId, connectFailure.getMessage());
            }
        }
    }
}
