package com.ispf.server.driver;

import com.ispf.driver.DriverMetadata;
import com.ispf.server.plugin.blueprint.SystemObjectStructureService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Embeds the device-driver variable schema (fixture blueprint) and configures driver defaults on DEVICE objects.
 */
@Service
public class DeviceProvisioningService {

    private final SystemObjectStructureService structureService;
    private final DriverCatalog driverCatalog;
    private final DriverRuntimeService driverRuntimeService;

    public DeviceProvisioningService(
            SystemObjectStructureService structureService,
            DriverCatalog driverCatalog,
            DriverRuntimeService driverRuntimeService
    ) {
        this.structureService = structureService;
        this.driverCatalog = driverCatalog;
        this.driverRuntimeService = driverRuntimeService;
    }

    public void provisionDriver(String devicePath, String driverId, Integer pollIntervalMs, boolean autoStart) {
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

        structureService.ensureDeviceDriverStructure(devicePath);

        int interval = pollIntervalMs != null && pollIntervalMs > 0 ? pollIntervalMs : 5000;
        Map<String, String> configuration = metadata.configurationSchema() != null
                ? Map.copyOf(metadata.configurationSchema())
                : Map.of();

        DriverBinding binding = DriverBinding.of(resolvedDriverId, interval, configuration, Map.of());
        driverRuntimeService.configure(devicePath, binding);
        if (autoStart) {
            driverRuntimeService.start(devicePath);
        }
    }
}
