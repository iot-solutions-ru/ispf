package com.ispf.server.api;

import com.ispf.server.platform.PlatformClusterDiagnosticsService;
import com.ispf.server.platform.PlatformClusterHealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform/cluster")
public class PlatformClusterHealthController {

    private final PlatformClusterHealthService healthService;
    private final PlatformClusterDiagnosticsService diagnosticsService;

    public PlatformClusterHealthController(
            PlatformClusterHealthService healthService,
            PlatformClusterDiagnosticsService diagnosticsService
    ) {
        this.healthService = healthService;
        this.diagnosticsService = diagnosticsService;
    }

    @GetMapping("/health")
    public PlatformClusterHealthService.ClusterHealth health() {
        return healthService.health();
    }

    @GetMapping("/diagnostics")
    public Map<String, Object> diagnostics(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return diagnosticsService.clusterDiagnostics(authorization);
    }
}
