package com.ispf.server.api;

import com.ispf.server.platform.PlatformStorageHealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/storage")
public class PlatformStorageHealthController {

    private final PlatformStorageHealthService healthService;

    public PlatformStorageHealthController(PlatformStorageHealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    public PlatformStorageHealthService.StorageHealth health() {
        return healthService.snapshot();
    }
}
