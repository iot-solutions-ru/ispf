package com.ispf.server.api;

import com.ispf.server.config.CommercialLicenseProperties;
import com.ispf.server.license.InstallationIdService;
import com.ispf.server.license.PlatformLicenseInfo;
import com.ispf.server.license.PlatformLicenseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformInstallationController {

    private final InstallationIdService installationIdService;
    private final PlatformLicenseService platformLicenseService;
    private final CommercialLicenseProperties licenseProperties;

    public PlatformInstallationController(
            InstallationIdService installationIdService,
            PlatformLicenseService platformLicenseService,
            CommercialLicenseProperties licenseProperties
    ) {
        this.installationIdService = installationIdService;
        this.platformLicenseService = platformLicenseService;
        this.licenseProperties = licenseProperties;
    }

    @GetMapping("/installation-id")
    public Map<String, String> installationId() {
        return Map.of("installationId", installationIdService.ensureInstallationId());
    }

    @GetMapping("/license")
    public PlatformLicenseInfo license() {
        return PlatformLicenseInfo.from(
                installationIdService,
                licenseProperties,
                platformLicenseService.currentStatus()
        );
    }
}
