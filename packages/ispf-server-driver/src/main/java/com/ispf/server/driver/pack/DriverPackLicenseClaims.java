package com.ispf.server.driver.pack;

import java.util.Map;

public record DriverPackLicenseClaims(
        String packId,
        String minPlatformVersion,
        String installationId,
        String jarSha256,
        String expiresAt,
        String signature
) {

    public static DriverPackLicenseClaims fromMap(Map<String, Object> license) {
        if (license == null || license.isEmpty()) {
            return null;
        }
        return new DriverPackLicenseClaims(
                stringValue(license.get("packId")),
                stringValue(license.get("minPlatformVersion")),
                stringValue(license.get("installationId")),
                stringValue(license.get("jarSha256")),
                stringValue(license.get("expiresAt")),
                stringValue(license.get("signature"))
        );
    }

    public Map<String, String> signingPayload() {
        return Map.of(
                "packId", packId,
                "minPlatformVersion", minPlatformVersion,
                "installationId", installationId,
                "jarSha256", jarSha256,
                "expiresAt", expiresAt
        );
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
