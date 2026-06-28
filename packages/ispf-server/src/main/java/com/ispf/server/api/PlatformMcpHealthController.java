package com.ispf.server.api;

import com.ispf.server.platform.PlatformMcpHealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/mcp")
public class PlatformMcpHealthController {

    private final PlatformMcpHealthService healthService;

    public PlatformMcpHealthController(PlatformMcpHealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    public PlatformMcpHealthService.McpHealth health() {
        return healthService.health();
    }
}
