package com.ispf.server.driver.pack;

import com.ispf.server.config.CommercialLicenseProperties;
import com.ispf.server.license.BundleManifestCanonicalizer;
import com.ispf.server.license.CommercialLicenseException;
import com.ispf.server.license.InstallationIdService;
import com.ispf.server.license.LicensePublicKeySupport;
import com.ispf.server.platform.update.PlatformVersionSupport;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class DriverPackLicenseVerifier {

    private final CommercialLicenseProperties properties;
    private final InstallationIdService installationIdService;
    private final Optional<BuildProperties> buildProperties;

    public DriverPackLicenseVerifier(
            CommercialLicenseProperties properties,
            InstallationIdService installationIdService,
            Optional<BuildProperties> buildProperties
    ) {
        this.properties = properties;
        this.installationIdService = installationIdService;
        this.buildProperties = buildProperties;
    }

    public void verifyOrSkip(String packId, Path jarPath, DriverPackLicenseClaims claims) {
        if (claims == null) {
            throw new CommercialLicenseException("Licensed driver pack requires license block: " + packId);
        }
        try {
            verify(packId, jarPath, claims);
        } catch (CommercialLicenseException ex) {
            if (properties.isEnforce()) {
                throw ex;
            }
            throw new CommercialLicenseException("Driver pack license invalid (enforce=false): " + ex.getMessage());
        }
    }

    public void verify(String packId, Path jarPath, DriverPackLicenseClaims claims) {
        requireField(claims.packId(), "packId");
        requireField(claims.minPlatformVersion(), "minPlatformVersion");
        requireField(claims.installationId(), "installationId");
        requireField(claims.jarSha256(), "jarSha256");
        requireField(claims.expiresAt(), "expiresAt");
        requireField(claims.signature(), "signature");

        if (!claims.packId().equals(packId)) {
            throw new CommercialLicenseException("License packId mismatch: expected " + packId);
        }

        String computedHash = sha256Hex(jarPath);
        if (!computedHash.equalsIgnoreCase(claims.jarSha256())) {
            throw new CommercialLicenseException("License jarSha256 mismatch");
        }

        String currentInstallation = installationIdService.currentInstallationId();
        if (!claims.installationId().equalsIgnoreCase(currentInstallation)) {
            throw new CommercialLicenseException("License installationId mismatch");
        }

        Instant expiry = Instant.parse(claims.expiresAt());
        if (Instant.now().isAfter(expiry)) {
            throw new CommercialLicenseException("License expired at " + claims.expiresAt());
        }

        String platformVersion = PlatformVersionSupport.currentVersion(buildProperties);
        if (PlatformVersionSupport.compare(platformVersion, claims.minPlatformVersion()) < 0) {
            throw new CommercialLicenseException(
                    "Platform version " + platformVersion + " below minPlatformVersion "
                            + claims.minPlatformVersion()
            );
        }

        String publicKeyPem = properties.getPublicKeyPem();
        if (publicKeyPem == null || publicKeyPem.isBlank()) {
            throw new CommercialLicenseException("ispf.license.public-key-pem is not configured");
        }

        String payload = BundleManifestCanonicalizer.canonicalJson(claims.signingPayload());
        LicensePublicKeySupport.verifyRsaSha256(payload, claims.signature(), publicKeyPem);
    }

    private static String sha256Hex(Path jarPath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(jarPath));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new CommercialLicenseException("Failed to hash driver JAR: " + ex.getMessage());
        }
    }

    private static void requireField(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new CommercialLicenseException("License field missing: " + name);
        }
    }
}
