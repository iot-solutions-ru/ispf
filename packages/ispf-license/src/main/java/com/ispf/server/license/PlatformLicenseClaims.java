package com.ispf.server.license;

import java.util.Map;

public record PlatformLicenseClaims(
        String tier,
        String minPlatformVersion,
        String installationId,
        String expiresAt,
        String signature
) {

    public static PlatformLicenseClaims fromMap(Map<String, Object> license) {
        if (license == null || license.isEmpty()) {
            return null;
        }
        return new PlatformLicenseClaims(
                stringValue(license.get("tier")),
                stringValue(license.get("minPlatformVersion")),
                stringValue(license.get("installationId")),
                stringValue(license.get("expiresAt")),
                stringValue(license.get("signature"))
        );
    }

    public Map<String, String> signingPayload() {
        return Map.of(
                "tier", tier,
                "minPlatformVersion", minPlatformVersion,
                "installationId", installationId,
                "expiresAt", expiresAt
        );
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
