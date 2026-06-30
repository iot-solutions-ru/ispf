package com.ispf.server.license;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

final class LicenseTestSupport {

    private LicenseTestSupport() {
    }

    static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    static String toPemPublicKey(KeyPair keyPair) {
        String base64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
    }

    static Map<String, Object> signedLicense(
            ObjectMapper objectMapper,
            Object manifestWithoutLicense,
            String appId,
            String installationId,
            KeyPair keyPair
    ) throws Exception {
        String contentSha256 = BundleManifestCanonicalizer.contentSha256(manifestWithoutLicense, objectMapper);
        String expiresAt = Instant.now().plus(365, ChronoUnit.DAYS).toString();
        Map<String, String> claims = new LinkedHashMap<>();
        claims.put("bundleId", appId);
        claims.put("minPlatformVersion", "0.0.0");
        claims.put("installationId", installationId);
        claims.put("contentSha256", contentSha256);
        claims.put("expiresAt", expiresAt);
        String payload = BundleManifestCanonicalizer.canonicalJson(claims);
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(payload.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> license = new LinkedHashMap<>(claims);
        license.put("signature", Base64.getEncoder().encodeToString(signature.sign()));
        return license;
    }

    static Map<String, Object> signedPlatformLicense(
            String tier,
            String installationId,
            KeyPair keyPair
    ) throws Exception {
        String expiresAt = Instant.now().plus(365, ChronoUnit.DAYS).toString();
        Map<String, String> claims = new LinkedHashMap<>();
        claims.put("tier", tier);
        claims.put("minPlatformVersion", "0.0.0");
        claims.put("installationId", installationId);
        claims.put("expiresAt", expiresAt);
        String payload = BundleManifestCanonicalizer.canonicalJson(claims);
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(payload.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> license = new LinkedHashMap<>(claims);
        license.put("signature", Base64.getEncoder().encodeToString(signature.sign()));
        return license;
    }
}
