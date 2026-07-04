package com.ispf.server.api;

import com.ispf.server.platform.PlatformClusterHealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/cluster")
public class PlatformClusterHealthController {

    private final PlatformClusterHealthService healthService;

    public PlatformClusterHealthController(PlatformClusterHealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    public PlatformClusterHealthService.ClusterHealth health() {
        return healthService.health();
    }
}
