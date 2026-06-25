package com.ispf.server.api;

import com.ispf.server.driver.DriverBinding;
import com.ispf.server.driver.DriverRuntimeService;
import com.ispf.server.driver.TelemetryPublishMode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/drivers/runtime")
public class DriverRuntimeController {

    private final DriverRuntimeService driverRuntimeService;

    public DriverRuntimeController(DriverRuntimeService driverRuntimeService) {
        this.driverRuntimeService = driverRuntimeService;
    }

    @GetMapping("/status")
    public DriverRuntimeService.DriverRuntimeStatus status(@RequestParam String devicePath) {
        return driverRuntimeService.status(devicePath)
                .orElseThrow(() -> new IllegalArgumentException("No driver binding for: " + devicePath));
    }

    @PostMapping("/start")
    public DriverRuntimeService.DriverRuntimeStatus start(@RequestParam String devicePath) {
        return driverRuntimeService.start(devicePath);
    }

    @PostMapping("/stop")
    public DriverRuntimeService.DriverRuntimeStatus stop(@RequestParam String devicePath) {
        return driverRuntimeService.stop(devicePath);
    }

    @PostMapping("/poll")
    public DriverRuntimeService.DriverRuntimeStatus poll(@RequestParam String devicePath) {
        return driverRuntimeService.pollNow(devicePath);
    }

    @PutMapping("/configure")
    public DriverRuntimeService.DriverRuntimeStatus configure(
            @RequestParam String devicePath,
            @RequestBody ConfigureDriverRequest request
    ) {
        Map<String, String> configuration = mergeConfiguration(request);
        TelemetryPublishMode publishMode = TelemetryPublishMode.parse(configuration.get("telemetryPublishMode"));
        int coalesceMs = parsePositiveInt(configuration.get("telemetryCoalesceMs"));
        DriverBinding binding = DriverBinding.of(
                request.driverId() != null ? request.driverId() : DriverBinding.DEFAULT_DRIVER_ID,
                request.pollIntervalMs() != null ? request.pollIntervalMs() : 2000,
                configuration,
                request.pointMappings() != null ? request.pointMappings() : Map.of(),
                publishMode,
                coalesceMs
        );
        driverRuntimeService.configure(devicePath, binding);
        if (Boolean.TRUE.equals(request.autoStart())) {
            return driverRuntimeService.start(devicePath);
        }
        return driverRuntimeService.status(devicePath).orElseThrow();
    }

    public record ConfigureDriverRequest(
            String driverId,
            Integer pollIntervalMs,
            Map<String, String> configuration,
            Map<String, String> pointMappings,
            String telemetryPublishMode,
            Integer telemetryCoalesceMs,
            Boolean autoStart
    ) {
    }

    private static Map<String, String> mergeConfiguration(ConfigureDriverRequest request) {
        Map<String, String> configuration = new java.util.LinkedHashMap<>(
                request.configuration() != null ? request.configuration() : Map.of()
        );
        if (request.telemetryPublishMode() != null && !request.telemetryPublishMode().isBlank()) {
            configuration.put("telemetryPublishMode", request.telemetryPublishMode().trim());
        }
        if (request.telemetryCoalesceMs() != null && request.telemetryCoalesceMs() > 0) {
            configuration.put("telemetryCoalesceMs", request.telemetryCoalesceMs().toString());
        }
        return Map.copyOf(configuration);
    }

    private static int parsePositiveInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
