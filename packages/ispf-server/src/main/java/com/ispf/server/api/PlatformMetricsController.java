package com.ispf.server.api;

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

    public PlatformMetricsController(PlatformMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", metricsService.snapshot().get("timestamp"));
        response.put("sections", metricsService.metricSections());
        return response;
    }
}
