package com.ispf.server.driver.pack;

import com.ispf.driver.pilot.PilotLicensedDeviceDriver;
import com.ispf.server.config.CommercialLicenseProperties;
import com.ispf.server.config.DriverPackProperties;
import com.ispf.server.license.BundleManifestCanonicalizer;
import com.ispf.server.license.InstallationIdService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FW-50 commercial driver pack pilot: signed JAR + manifest loads into {@link LicensedDriverRegistry}.
 */
class LicensedDriverPackPilotTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsSignedPilotPackWithEnforcedLicense() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        CommercialLicenseProperties licenseProperties = new CommercialLicenseProperties();
        licenseProperties.setPublicKeyPem(toPemPublicKey(keyPair));
        licenseProperties.setEnforce(true);
        licenseProperties.setDataDir(tempDir.resolve("license-data").toString());

        InstallationIdService installationIdService = new InstallationIdService(licenseProperties);
        installationIdService.ensureInstallationId();
        String installationId = installationIdService.currentInstallationId();

        Path packsRoot = tempDir.resolve("packs");
        Path packDir = packsRoot.resolve("pilot-licensed-pack");
        Files.createDirectories(packDir);

        Path jarPath = packDir.resolve("pilot-licensed-pack.jar");
        writePilotDriverJar(jarPath);

        String jarSha256 = sha256Hex(jarPath);
        String expiresAt = Instant.now().plus(90, ChronoUnit.DAYS).toString();
        Map<String, String> licensePayload = new LinkedHashMap<>();
        licensePayload.put("packId", "pilot-licensed-pack");
        licensePayload.put("minPlatformVersion", "0.0.0");
        licensePayload.put("installationId", installationId);
        licensePayload.put("jarSha256", jarSha256);
        licensePayload.put("expiresAt", expiresAt);
        String signature = signLicense(keyPair, licensePayload);

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("packId", "pilot-licensed-pack");
        manifest.put("minPlatformVersion", "0.0.0");
        manifest.put("jarFile", "pilot-licensed-pack.jar");
        manifest.put("drivers", List.of(Map.of(
                "driverId", "pilot-licensed",
                "driverClass", PilotLicensedDeviceDriver.class.getName()
        )));
        manifest.put("license", Map.of(
                "packId", "pilot-licensed-pack",
                "minPlatformVersion", "0.0.0",
                "installationId", installationId,
                "jarSha256", jarSha256,
                "expiresAt", expiresAt,
                "signature", signature
        ));

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.writeValue(packDir.resolve("driver-pack.json").toFile(), manifest);

        DriverPackProperties packProperties = new DriverPackProperties();
        packProperties.setPacksDir(packsRoot.toString());

        LicensedDriverRegistry registry = new LicensedDriverRegistry();
        DriverPackLicenseVerifier verifier = new DriverPackLicenseVerifier(
                licenseProperties,
                installationIdService,
                Optional.empty()
        );
        LicensedDriverPackLoader loader = new LicensedDriverPackLoader(
                packProperties,
                licenseProperties,
                verifier,
                registry,
                objectMapper
        );

        loader.loadPackDirectory(packDir);

        assertTrue(registry.contains("pilot-licensed"));
        assertEquals("pilot-licensed", registry.metadata().getFirst().id());
        var driver = registry.create("pilot-licensed");
        assertTrue(!driver.isConnected());
        driver.connect();
        assertTrue(driver.isConnected());
    }

    private void writePilotDriverJar(Path jarPath) throws IOException {
        String classResource = PilotLicensedDeviceDriver.class.getName().replace('.', '/') + ".class";
        try (InputStream in = PilotLicensedDeviceDriver.class.getClassLoader().getResourceAsStream(classResource)) {
            if (in == null) {
                throw new IOException("Pilot driver class not on test classpath: " + classResource);
            }
            byte[] classBytes = in.readAllBytes();
            try (var jarOut = new java.util.jar.JarOutputStream(Files.newOutputStream(jarPath))) {
                var entry = new java.util.jar.JarEntry(classResource);
                jarOut.putNextEntry(entry);
                jarOut.write(classBytes);
                jarOut.closeEntry();
            }
        }
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String toPemPublicKey(KeyPair keyPair) {
        String base64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
    }

    private static String signLicense(KeyPair keyPair, Map<String, String> payload) throws Exception {
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
