package com.ispf.server.platform.analytics.pack;

import com.ispf.server.config.AnalyticsPackProperties;
import com.ispf.server.config.CommercialLicenseProperties;
import com.ispf.server.driver.pack.DriverPackLicenseClaims;
import com.ispf.server.driver.pack.DriverPackLicenseVerifier;
import com.ispf.server.license.BundleManifestCanonicalizer;
import com.ispf.server.license.InstallationIdService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BL-216: signed analytics-pack JAR + manifest loads via {@link DropInAnalyticsPackLoader}.
 */
class AnalyticsPackLicensePilotTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsSignedCommercialPackWithEnforcedLicense() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        CommercialLicenseProperties licenseProperties = new CommercialLicenseProperties();
        licenseProperties.setPublicKeyPem(toPemPublicKey(keyPair));
        licenseProperties.setEnforce(true);
        licenseProperties.setDataDir(tempDir.resolve("license-data").toString());

        InstallationIdService installationIdService = new InstallationIdService(licenseProperties);
        installationIdService.ensureInstallationId();
        String installationId = installationIdService.currentInstallationId();

        Path packsRoot = tempDir.resolve("packs");
        Path packDir = packsRoot.resolve("pilot-analytics-pack");
        Files.createDirectories(packDir);

        Path jarPath = packDir.resolve("pilot-analytics-pack.jar");
        writeEmptyProviderJar(jarPath);

        String jarSha256 = sha256Hex(jarPath);
        String expiresAt = Instant.now().plus(90, ChronoUnit.DAYS).toString();
        Map<String, String> licensePayload = new LinkedHashMap<>();
        licensePayload.put("packId", "pilot-analytics-pack");
        licensePayload.put("minPlatformVersion", "0.0.0");
        licensePayload.put("installationId", installationId);
        licensePayload.put("jarSha256", jarSha256);
        licensePayload.put("expiresAt", expiresAt);
        String signature = signLicense(keyPair, licensePayload);

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("packId", "pilot-analytics-pack");
        manifest.put("version", "1.0.0");
        manifest.put("licenseType", "commercial");
        manifest.put("minPlatformVersion", "0.0.0");
        manifest.put("jarFile", "pilot-analytics-pack.jar");
        manifest.put("functions", List.of("pilotMetric"));
        manifest.put("license", Map.of(
                "packId", "pilot-analytics-pack",
                "minPlatformVersion", "0.0.0",
                "installationId", installationId,
                "jarSha256", jarSha256,
                "expiresAt", expiresAt,
                "signature", signature
        ));

        new ObjectMapper().writeValue(packDir.resolve("analytics-pack.json").toFile(), manifest);

        AnalyticsPackProperties packProperties = new AnalyticsPackProperties();
        packProperties.setPacksDir(packsRoot.toString());
        AnalyticsExtensionRegistry registry = new AnalyticsExtensionRegistry();
        AnalyticsPackLoader packLoader = new AnalyticsPackLoader(registry);
        DriverPackLicenseVerifier licenseVerifier = new DriverPackLicenseVerifier(
                licenseProperties,
                installationIdService,
                Optional.empty()
        );
        AnalyticsPackLicenseSigner licenseSigner = new AnalyticsPackLicenseSigner(
                licenseProperties,
                installationIdService,
                Optional.empty(),
                licenseVerifier
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> licenseMap = (Map<String, Object>) manifest.get("license");
        licenseSigner.verify(
                "pilot-analytics-pack",
                jarPath,
                DriverPackLicenseClaims.fromMap(licenseMap)
        );
        assertTrue(Files.exists(packDir.resolve("analytics-pack.json")));
    }

    @Test
    void rejectsTamperedJarWhenEnforced() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        CommercialLicenseProperties licenseProperties = new CommercialLicenseProperties();
        licenseProperties.setPublicKeyPem(toPemPublicKey(keyPair));
        licenseProperties.setEnforce(true);
        licenseProperties.setDataDir(tempDir.resolve("license-data").toString());

        InstallationIdService installationIdService = new InstallationIdService(licenseProperties);
        installationIdService.ensureInstallationId();
        String installationId = installationIdService.currentInstallationId();

        Path packsRoot = tempDir.resolve("packs");
        Path packDir = packsRoot.resolve("tampered-pack");
        Files.createDirectories(packDir);

        Path jarPath = packDir.resolve("tampered-pack.jar");
        writeEmptyProviderJar(jarPath);
        String jarSha256 = sha256Hex(jarPath);
        String expiresAt = Instant.now().plus(90, ChronoUnit.DAYS).toString();
        Map<String, String> licensePayload = new LinkedHashMap<>();
        licensePayload.put("packId", "tampered-pack");
        licensePayload.put("minPlatformVersion", "0.0.0");
        licensePayload.put("installationId", installationId);
        licensePayload.put("jarSha256", jarSha256);
        licensePayload.put("expiresAt", expiresAt);
        String signature = signLicense(keyPair, licensePayload);

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("packId", "tampered-pack");
        manifest.put("licenseType", "commercial");
        manifest.put("jarFile", "tampered-pack.jar");
        manifest.put("license", Map.of(
                "packId", "tampered-pack",
                "minPlatformVersion", "0.0.0",
                "installationId", installationId,
                "jarSha256", jarSha256,
                "expiresAt", expiresAt,
                "signature", signature
        ));
        new ObjectMapper().writeValue(packDir.resolve("analytics-pack.json").toFile(), manifest);

        Files.writeString(jarPath, "tampered", StandardCharsets.UTF_8);

        AnalyticsPackProperties packProperties = new AnalyticsPackProperties();
        packProperties.setPacksDir(packsRoot.toString());
        DropInAnalyticsPackLoader loader = new DropInAnalyticsPackLoader(
                packProperties,
                new AnalyticsPackLoader(new AnalyticsExtensionRegistry()),
                new ObjectMapper(),
                licenseProperties,
                new AnalyticsPackLicenseSigner(
                        licenseProperties,
                        installationIdService,
                        Optional.empty(),
                        new DriverPackLicenseVerifier(licenseProperties, installationIdService, Optional.empty())
                )
        );

        List<String> helpers = loader.loadPackDirectory(packDir);
        assertTrue(helpers.isEmpty());
    }

    private static void writeEmptyProviderJar(Path jarPath) throws IOException {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
            jar.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
            jar.write("Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String toPemPublicKey(KeyPair keyPair) {
        byte[] encoded = keyPair.getPublic().getEncoded();
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(encoded);
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----\n";
    }

    private static String sha256Hex(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
    }

    private static String signLicense(KeyPair keyPair, Map<String, String> payload) throws Exception {
        String canonical = BundleManifestCanonicalizer.canonicalJson(payload);
        java.security.Signature signature = java.security.Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(canonical.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }
}
