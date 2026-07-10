package com.ispf.server.platform.analytics.pack;

import com.ispf.server.config.CommercialLicenseProperties;
import com.ispf.server.driver.pack.DriverPackLicenseClaims;
import com.ispf.server.driver.pack.DriverPackLicenseVerifier;
import com.ispf.server.license.BundleManifestCanonicalizer;
import com.ispf.server.license.CommercialLicenseException;
import com.ispf.server.license.InstallationIdService;
import com.ispf.server.platform.update.PlatformVersionSupport;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * RSA signing for commercial analytics-pack manifests (BL-216).
 */
@Service
public class AnalyticsPackLicenseSigner {

    private final CommercialLicenseProperties properties;
    private final InstallationIdService installationIdService;
    private final Optional<BuildProperties> buildProperties;
    private final DriverPackLicenseVerifier licenseVerifier;

    public AnalyticsPackLicenseSigner(
            CommercialLicenseProperties properties,
            InstallationIdService installationIdService,
            Optional<BuildProperties> buildProperties,
            DriverPackLicenseVerifier licenseVerifier
    ) {
        this.properties = properties;
        this.installationIdService = installationIdService;
        this.buildProperties = buildProperties;
        this.licenseVerifier = licenseVerifier;
    }

    public boolean isConfigured() {
        String pem = properties.getSigningPrivateKeyPem();
        return pem != null && !pem.isBlank();
    }

    public Map<String, Object> signManifestIfNeeded(
            String packId,
            Map<String, Object> manifest,
            Path jarPath
    ) {
        if (manifest.get("license") != null || !isConfigured()) {
            return manifest;
        }
        String installationId = installationIdService.ensureInstallationId();
        String jarSha256 = sha256Hex(jarPath);
        String expiresAt = Instant.now().plus(365, ChronoUnit.DAYS).toString();
        String minPlatformVersion = PlatformVersionSupport.currentVersion(buildProperties);
        Map<String, String> claims = new LinkedHashMap<>();
        claims.put("packId", packId);
        claims.put("minPlatformVersion", minPlatformVersion);
        claims.put("installationId", installationId);
        claims.put("jarSha256", jarSha256);
        claims.put("expiresAt", expiresAt);
        try {
            String payload = BundleManifestCanonicalizer.canonicalJson(claims);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(loadPrivateKey(properties.getSigningPrivateKeyPem()));
            signature.update(payload.getBytes(StandardCharsets.UTF_8));
            Map<String, Object> license = new LinkedHashMap<>(claims);
            license.put("signature", Base64.getEncoder().encodeToString(signature.sign()));
            manifest.put("license", license);
            return manifest;
        } catch (CommercialLicenseException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new CommercialLicenseException("Failed to sign analytics pack: " + ex.getMessage());
        }
    }

    public void verifyOrSkip(String packId, Path jarPath, DriverPackLicenseClaims claims) {
        licenseVerifier.verifyOrSkip(packId, jarPath, claims);
    }

    public void verify(String packId, Path jarPath, DriverPackLicenseClaims claims) {
        licenseVerifier.verify(packId, jarPath, claims);
    }

    private static String sha256Hex(Path jarPath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(jarPath));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new CommercialLicenseException("Failed to hash analytics pack JAR: " + ex.getMessage());
        }
    }

    private static PrivateKey loadPrivateKey(String pem) throws Exception {
        String normalized = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(normalized);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }
}
