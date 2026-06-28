package com.ispf.server.api;

import com.ispf.server.report.PlatformYargHealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/reports/yarg")
public class PlatformYargHealthController {

    private final PlatformYargHealthService healthService;

    public PlatformYargHealthController(PlatformYargHealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    public PlatformYargHealthService.YargHealth health() {
        return healthService.health();
    }
}
