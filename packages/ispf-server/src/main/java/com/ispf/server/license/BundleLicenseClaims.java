package com.ispf.server.license;

import java.util.Map;

public record BundleLicenseClaims(
        String bundleId,
        String minPlatformVersion,
        String installationId,
        String contentSha256,
        String expiresAt,
        String signature
) {

    public static BundleLicenseClaims fromMap(Map<String, Object> license) {
        if (license == null || license.isEmpty()) {
            return null;
        }
        return new BundleLicenseClaims(
                stringValue(license.get("bundleId")),
                stringValue(license.get("minPlatformVersion")),
                stringValue(license.get("installationId")),
                stringValue(license.get("contentSha256")),
                stringValue(license.get("expiresAt")),
                stringValue(license.get("signature"))
        );
    }

    public Map<String, String> signingPayload() {
        return Map.of(
                "bundleId", bundleId,
                "minPlatformVersion", minPlatformVersion,
                "installationId", installationId,
                "contentSha256", contentSha256,
                "expiresAt", expiresAt
        );
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
