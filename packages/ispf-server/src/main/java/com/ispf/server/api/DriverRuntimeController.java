package com.ispf.server.api;

import com.ispf.server.driver.DriverBinding;
import com.ispf.server.driver.DriverRuntimeService;
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
        DriverBinding binding = new DriverBinding(
                request.driverId() != null ? request.driverId() : DriverBinding.DEFAULT_DRIVER_ID,
                request.pollIntervalMs() != null ? request.pollIntervalMs() : 2000,
                request.configuration() != null ? request.configuration() : Map.of(),
                request.pointMappings() != null ? request.pointMappings() : Map.of()
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
            Boolean autoStart
    ) {
    }
}
