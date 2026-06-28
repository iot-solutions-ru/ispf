package com.ispf.server.api;

import com.ispf.server.platform.PlatformRedisHealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/redis")
public class PlatformRedisHealthController {

    private final PlatformRedisHealthService healthService;

    public PlatformRedisHealthController(PlatformRedisHealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    public PlatformRedisHealthService.RedisHealth health() {
        return healthService.health();
    }
}
