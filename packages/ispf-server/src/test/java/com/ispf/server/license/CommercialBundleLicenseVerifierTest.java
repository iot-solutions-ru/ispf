package com.ispf.server.license;

import com.ispf.server.config.CommercialLicenseProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.security.KeyPair;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommercialBundleLicenseVerifierTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private KeyPair keyPair;
    private CommercialLicenseProperties properties;
    private InstallationIdService installationIdService;
    private CommercialBundleLicenseVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = LicenseTestSupport.generateRsaKeyPair();
        properties = new CommercialLicenseProperties();
        properties.setPublicKeyPem(LicenseTestSupport.toPemPublicKey(keyPair));
        properties.setEnforce(true);
        properties.setDataDir(System.getProperty("java.io.tmpdir") + "/ispf-license-unit");
        installationIdService = new InstallationIdService(properties);
        String installationId = installationIdService.ensureInstallationId();
        verifier = new CommercialBundleLicenseVerifier(
                properties,
                installationIdService,
                objectMapper,
                Optional.empty()
        );
    }

    @Test
    void acceptsValidSignedLicense() throws Exception {
        Map<String, Object> manifest = baseManifest("licensed-app");
        manifest.put("license", LicenseTestSupport.signedLicense(
                objectMapper,
                manifest,
                "licensed-app",
                installationIdService.currentInstallationId(),
                keyPair
        ));
        assertDoesNotThrow(() -> verifier.verifyOrWarn("licensed-app", manifest));
    }

    @Test
    void rejectsTamperedContentHash() throws Exception {
        Map<String, Object> manifest = baseManifest("licensed-app");
        Map<String, Object> license = LicenseTestSupport.signedLicense(
                objectMapper,
                manifest,
                "licensed-app",
                installationIdService.currentInstallationId(),
                keyPair
        );
        license.put("contentSha256", "00".repeat(64));
        manifest.put("license", license);
        assertThrows(CommercialLicenseException.class, () -> verifier.verifyOrWarn("licensed-app", manifest));
    }

    @Test
    void rejectsExpiredLicense() throws Exception {
        Map<String, Object> manifest = baseManifest("licensed-app");
        String installationId = installationIdService.currentInstallationId();
        String contentSha256 = BundleManifestCanonicalizer.contentSha256(manifest, objectMapper);
        String expiresAt = Instant.now().minus(1, ChronoUnit.DAYS).toString();
        Map<String, String> claims = Map.of(
                "bundleId", "licensed-app",
                "minPlatformVersion", "0.1.0",
                "installationId", installationId,
                "contentSha256", contentSha256,
                "expiresAt", expiresAt
        );
        String payload = BundleManifestCanonicalizer.canonicalJson(claims);
        java.security.Signature signature = java.security.Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Map<String, Object> license = new LinkedHashMap<>(claims);
        license.put("signature", java.util.Base64.getEncoder().encodeToString(signature.sign()));
        manifest.put("license", license);
        assertThrows(CommercialLicenseException.class, () -> verifier.verifyOrWarn("licensed-app", manifest));
    }

    @Test
    void requireSignedBundlesRejectsUnsignedManifest() {
        properties.setRequireSignedBundles(true);
        properties.setEnforce(false);
        Map<String, Object> manifest = baseManifest("unsigned-app");
        assertThrows(CommercialLicenseException.class, () -> verifier.verifyOrWarn("unsigned-app", manifest));
    }

    @Test
    void requireSignedBundlesAllowsTrustedMarketplaceFreeInstall() {
        properties.setRequireSignedBundles(true);
        properties.setEnforce(false);
        Map<String, Object> manifest = baseManifest("free-marketplace-app");
        assertDoesNotThrow(() -> verifier.verifyOrWarn("free-marketplace-app", manifest, true));
    }

    @Test
    void requireSignedBundlesAcceptsValidSignedLicense() throws Exception {
        properties.setRequireSignedBundles(true);
        properties.setEnforce(false);
        Map<String, Object> manifest = baseManifest("signed-required");
        manifest.put("license", LicenseTestSupport.signedLicense(
                objectMapper,
                manifest,
                "signed-required",
                installationIdService.currentInstallationId(),
                keyPair
        ));
        assertDoesNotThrow(() -> verifier.verifyOrWarn("signed-required", manifest));
    }

    @Test
    void requireSignedBundlesValidationRejectsUnsignedManifest() {
        properties.setRequireSignedBundles(true);
        Map<String, Object> manifest = baseManifest("unsigned-validate");
        assertThrows(CommercialLicenseException.class, () -> verifier.verifyForValidation("unsigned-validate", manifest));
    }

    private static Map<String, Object> baseManifest(String appId) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("version", "1.0.0");
        manifest.put("displayName", appId);
        manifest.put("schemaName", "app_" + appId.replace('-', '_'));
        manifest.put("migrations", java.util.List.of());
        manifest.put("functions", java.util.List.of());
        return manifest;
    }
}
