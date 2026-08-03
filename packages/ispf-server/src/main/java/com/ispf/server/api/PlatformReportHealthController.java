package com.ispf.server.api;

import com.ispf.server.report.PlatformReportHealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/reports")
public class PlatformReportHealthController {

    private final PlatformReportHealthService healthService;

    public PlatformReportHealthController(PlatformReportHealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    public PlatformReportHealthService.ReportHealth health() {
        return healthService.health();
    }
}
