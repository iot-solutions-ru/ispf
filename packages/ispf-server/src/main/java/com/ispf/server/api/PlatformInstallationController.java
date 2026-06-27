package com.ispf.server.api;

import com.ispf.server.license.InstallationIdService;
import com.ispf.server.license.PlatformLicenseService;
import com.ispf.server.license.PlatformLicenseStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformInstallationController {

    private final InstallationIdService installationIdService;
    private final PlatformLicenseService platformLicenseService;

    public PlatformInstallationController(
            InstallationIdService installationIdService,
            PlatformLicenseService platformLicenseService
    ) {
        this.installationIdService = installationIdService;
        this.platformLicenseService = platformLicenseService;
    }

    @GetMapping("/installation-id")
    public Map<String, String> installationId() {
        return Map.of("installationId", installationIdService.ensureInstallationId());
    }

    @GetMapping("/license")
    public PlatformLicenseStatus license() {
        return platformLicenseService.currentStatus();
    }
}
