package com.ispf.server.api;

import com.ispf.server.platform.PlatformMetricsProbeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform/diagnostics")
public class PlatformDiagnosticsController {

    private final PlatformMetricsProbeService metricsProbeService;

    public PlatformDiagnosticsController(PlatformMetricsProbeService metricsProbeService) {
        this.metricsProbeService = metricsProbeService;
    }

    @GetMapping("/metrics-probe")
    public Map<String, Object> metricsProbeStatus() {
        return Map.of(
                "enabled", metricsProbeService.isDiagnosticsProbeEnabled(),
                "devicePath", PlatformMetricsProbeService.DEVICE_PATH,
                "devicePresent", metricsProbeService.probeDeviceExists()
        );
    }

    @PutMapping("/metrics-probe")
    public Map<String, Object> setMetricsProbe(@RequestBody MetricsProbeRequest request) {
        boolean enabled = request.enabled() != null && request.enabled();
        metricsProbeService.setDiagnosticsProbeEnabled(enabled);
        return Map.of(
                "enabled", metricsProbeService.isDiagnosticsProbeEnabled(),
                "devicePath", PlatformMetricsProbeService.DEVICE_PATH,
                "devicePresent", metricsProbeService.probeDeviceExists()
        );
    }

    public record MetricsProbeRequest(Boolean enabled) {
    }
}
