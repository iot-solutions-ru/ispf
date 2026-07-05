package com.ispf.server.driver;

import com.ispf.driver.DriverMetadata;
import com.ispf.server.plugin.blueprint.SystemObjectStructureService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
        // #region agent log
        agentLog("DeviceProvisioningService.java:provisionDriver:entry", "provisionDriver called", "H3", Map.of(
                "devicePath", devicePath,
                "requestedDriverId", resolvedDriverId,
                "existingDriverId", driverRuntimeService.debugReadDriverId(devicePath).orElse("(none)")
        ));
        // #endregion
        DriverMetadata metadata = driverCatalog.list().stream()
                .filter(driver -> driver.id().equals(resolvedDriverId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unknown driverId: " + resolvedDriverId
                ));

        structureService.ensureDeviceDriverStructure(devicePath);
        // #region agent log
        agentLog("DeviceProvisioningService.java:provisionDriver:afterStructure", "device-driver structure ensured", "H3", Map.of(
                "devicePath", devicePath,
                "requestedDriverId", resolvedDriverId,
                "existingDriverId", driverRuntimeService.debugReadDriverId(devicePath).orElse("(none)")
        ));
        // #endregion

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

    // #region agent log
    private static void agentLog(String location, String message, String hypothesisId, Map<String, Object> data) {
        try {
            String json = "{\"sessionId\":\"c91425\",\"timestamp\":" + System.currentTimeMillis()
                    + ",\"location\":\"" + location + "\",\"message\":\"" + message + "\",\"hypothesisId\":\""
                    + hypothesisId + "\",\"data\":" + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data) + "}\n";
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
