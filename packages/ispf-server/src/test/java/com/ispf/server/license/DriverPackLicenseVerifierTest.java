package com.ispf.server.license;

import com.ispf.server.config.CommercialLicenseProperties;
import com.ispf.server.driver.pack.DriverPackLicenseClaims;
import com.ispf.server.driver.pack.DriverPackLicenseVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DriverPackLicenseVerifierTest {

    @TempDir
    Path tempDir;

    private KeyPair keyPair;
    private CommercialLicenseProperties properties;
    private InstallationIdService installationIdService;
    private DriverPackLicenseVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = LicenseTestSupport.generateRsaKeyPair();
        properties = new CommercialLicenseProperties();
        properties.setPublicKeyPem(LicenseTestSupport.toPemPublicKey(keyPair));
        properties.setEnforce(true);
        properties.setDataDir(tempDir.toString());
        installationIdService = new InstallationIdService(properties);
        installationIdService.ensureInstallationId();
        verifier = new DriverPackLicenseVerifier(properties, installationIdService, Optional.empty());
    }

    @Test
    void acceptsValidSignedJarLicense() throws Exception {
        Path jarPath = tempDir.resolve("pack.jar");
        Files.writeString(jarPath, "driver-bytes", StandardCharsets.UTF_8);
        DriverPackLicenseClaims claims = signedClaims("demo-pack", jarPath);
        assertDoesNotThrow(() -> verifier.verify("demo-pack", jarPath, claims));
    }

    @Test
    void rejectsTamperedJarHash() throws Exception {
        Path jarPath = tempDir.resolve("pack.jar");
        Files.writeString(jarPath, "driver-bytes", StandardCharsets.UTF_8);
        DriverPackLicenseClaims claims = signedClaims("demo-pack", jarPath);
        DriverPackLicenseClaims tampered = new DriverPackLicenseClaims(
                claims.packId(),
                claims.minPlatformVersion(),
                claims.installationId(),
                "00".repeat(64),
                claims.expiresAt(),
                claims.signature()
        );
        assertThrows(CommercialLicenseException.class, () -> verifier.verify("demo-pack", jarPath, tampered));
    }

    @Test
    void rejectsExpiredLicense() throws Exception {
        Path jarPath = tempDir.resolve("pack.jar");
        Files.writeString(jarPath, "driver-bytes", StandardCharsets.UTF_8);
        String jarSha256 = sha256Hex(jarPath);
        String installationId = installationIdService.currentInstallationId();
        String expiresAt = Instant.now().minus(1, ChronoUnit.DAYS).toString();
        Map<String, String> payload = Map.of(
                "packId", "demo-pack",
                "minPlatformVersion", "0.1.0",
                "installationId", installationId,
                "jarSha256", jarSha256,
                "expiresAt", expiresAt
        );
        String signature = sign(payload);
        DriverPackLicenseClaims claims = new DriverPackLicenseClaims(
                "demo-pack", "0.1.0", installationId, jarSha256, expiresAt, signature
        );
        assertThrows(CommercialLicenseException.class, () -> verifier.verify("demo-pack", jarPath, claims));
    }

    private DriverPackLicenseClaims signedClaims(String packId, Path jarPath) throws Exception {
        String jarSha256 = sha256Hex(jarPath);
        String installationId = installationIdService.currentInstallationId();
        String expiresAt = Instant.now().plus(30, ChronoUnit.DAYS).toString();
        Map<String, String> payload = Map.of(
                "packId", packId,
                "minPlatformVersion", "0.1.0",
                "installationId", installationId,
                "jarSha256", jarSha256,
                "expiresAt", expiresAt
        );
        return new DriverPackLicenseClaims(
                packId, "0.1.0", installationId, jarSha256, expiresAt, sign(payload)
        );
    }

    private String sign(Map<String, String> payload) throws Exception {
        String canonical = BundleManifestCanonicalizer.canonicalJson(payload);
        java.security.Signature signature = java.security.Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(canonical.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    private static String sha256Hex(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(Files.readAllBytes(path));
        return HexFormat.of().formatHex(hash);
    }
}
