package com.ispf.server.license;

public record PlatformLicenseStatus(
        String mode,
        String tier,
        String expiresAt,
        boolean valid,
        String message
) {
}
