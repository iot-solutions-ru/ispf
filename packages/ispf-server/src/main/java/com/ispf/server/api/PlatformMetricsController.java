package com.ispf.server.api;

import com.ispf.server.platform.PlatformDiagnosticsService;
import com.ispf.server.platform.PlatformMetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformMetricsController {

    private final PlatformMetricsService metricsService;
    private final PlatformDiagnosticsService diagnosticsService;

    public PlatformMetricsController(
            PlatformMetricsService metricsService,
            PlatformDiagnosticsService diagnosticsService
    ) {
        this.metricsService = metricsService;
        this.diagnosticsService = diagnosticsService;
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> diagnostics = diagnosticsService.snapshot();
        response.put("timestamp", metricsService.snapshot().get("timestamp"));
        response.put("replicaId", diagnostics.get("replicaId"));
        response.put("replicaProfile", diagnostics.get("replicaProfile"));
        response.put("serverPort", diagnostics.get("serverPort"));
        response.put("diagnostics", diagnostics);
        response.put("sections", metricsService.metricSections(diagnostics));
        return response;
    }
}
