package com.ispf.server.license;

import com.ispf.server.config.CommercialLicenseProperties;

public record PlatformLicenseInfo(
        String installationId,
        boolean enforce,
        String mode,
        String tier,
        String expiresAt,
        boolean valid,
        String message
) {
    public static PlatformLicenseInfo from(
            InstallationIdService installationIdService,
            CommercialLicenseProperties properties,
            PlatformLicenseStatus status
    ) {
        return new PlatformLicenseInfo(
                installationIdService.ensureInstallationId(),
                properties.isEnforce(),
                status.mode(),
                status.tier(),
                status.expiresAt(),
                status.valid(),
                status.message()
        );
    }
}
