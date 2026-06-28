package com.ispf.server.api;

import com.ispf.server.platform.PlatformNatsHealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/nats")
public class PlatformNatsHealthController {

    private final PlatformNatsHealthService healthService;

    public PlatformNatsHealthController(PlatformNatsHealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    public PlatformNatsHealthService.NatsHealth health() {
        return healthService.health();
    }
}
