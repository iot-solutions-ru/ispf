package com.ispf.server.api;

import com.ispf.server.license.InstallationIdService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformInstallationController {

    private final InstallationIdService installationIdService;

    public PlatformInstallationController(InstallationIdService installationIdService) {
        this.installationIdService = installationIdService;
    }

    @GetMapping("/installation-id")
    public Map<String, String> installationId() {
        return Map.of("installationId", installationIdService.ensureInstallationId());
    }
}
