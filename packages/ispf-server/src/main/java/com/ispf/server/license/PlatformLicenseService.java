package com.ispf.server.license;

import com.ispf.server.config.CommercialLicenseProperties;
import com.ispf.server.platform.update.PlatformVersionSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
public class PlatformLicenseService {

    private static final Logger log = LoggerFactory.getLogger(PlatformLicenseService.class);
    private static final String LICENSE_FILE = "platform-license.json";

    private final CommercialLicenseProperties properties;
    private final InstallationIdService installationIdService;
    private final ObjectMapper objectMapper;
    private final Optional<BuildProperties> buildProperties;
    private volatile PlatformLicenseStatus cachedStatus;

    public PlatformLicenseService(
            CommercialLicenseProperties properties,
            InstallationIdService installationIdService,
            ObjectMapper objectMapper,
            Optional<BuildProperties> buildProperties
    ) {
        this.properties = properties;
        this.installationIdService = installationIdService;
        this.objectMapper = objectMapper;
        this.buildProperties = buildProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void enforceOnStartup() {
        PlatformLicenseStatus status = currentStatus();
        if ("community".equals(status.mode())) {
            log.info("Platform license mode: community (AGPL)");
            return;
        }
        if (status.valid()) {
            log.info("Platform license active: tier={} expiresAt={}", status.tier(), status.expiresAt());
            return;
        }
        if (properties.isEnforce()) {
            log.error("Platform license invalid: {}", status.message());
            throw new IllegalStateException("ISPF license enforcement failed: " + status.message());
        }
        log.warn("Platform license invalid (enforce=false): {}", status.message());
    }

    public PlatformLicenseStatus currentStatus() {
        PlatformLicenseStatus cached = cachedStatus;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (cachedStatus != null) {
                return cachedStatus;
            }
            cachedStatus = loadStatus();
            return cachedStatus;
        }
    }

    private PlatformLicenseStatus loadStatus() {
        Path licensePath = Path.of(properties.getDataDir()).resolve(LICENSE_FILE).normalize();
        if (!Files.isRegularFile(licensePath)) {
            return new PlatformLicenseStatus("community", "community", null, true, "No platform-license.json — AGPL community mode");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> root = objectMapper.readValue(licensePath.toFile(), Map.class);
            PlatformLicenseClaims claims = PlatformLicenseClaims.fromMap(root);
            if (claims == null) {
                return invalid("platform-license.json is empty");
            }
            verify(claims);
            return new PlatformLicenseStatus("commercial", claims.tier(), claims.expiresAt(), true, "Valid commercial platform license");
        } catch (CommercialLicenseException ex) {
            return invalid(ex.getMessage());
        } catch (Exception ex) {
            return invalid("Failed to read platform license: " + ex.getMessage());
        }
    }

    private PlatformLicenseStatus invalid(String message) {
        return new PlatformLicenseStatus("commercial", null, null, false, message);
    }

    private void verify(PlatformLicenseClaims claims) {
        requireField(claims.tier(), "tier");
        requireField(claims.minPlatformVersion(), "minPlatformVersion");
        requireField(claims.installationId(), "installationId");
        requireField(claims.expiresAt(), "expiresAt");
        requireField(claims.signature(), "signature");

        if (!claims.installationId().equalsIgnoreCase(installationIdService.currentInstallationId())) {
            throw new CommercialLicenseException("Platform license installationId mismatch");
        }

        Instant expiry = Instant.parse(claims.expiresAt());
        if (Instant.now().isAfter(expiry)) {
            throw new CommercialLicenseException("Platform license expired at " + claims.expiresAt());
        }

        String platformVersion = PlatformVersionSupport.currentVersion(buildProperties);
        if (PlatformVersionSupport.compare(platformVersion, claims.minPlatformVersion()) < 0) {
            throw new CommercialLicenseException(
                    "Platform version " + platformVersion + " below minPlatformVersion " + claims.minPlatformVersion()
            );
        }

        String publicKeyPem = properties.getPublicKeyPem();
        if (publicKeyPem == null || publicKeyPem.isBlank()) {
            throw new CommercialLicenseException("ispf.license.public-key-pem is not configured");
        }

        String payload = BundleManifestCanonicalizer.canonicalJson(claims.signingPayload());
        LicensePublicKeySupport.verifyRsaSha256(payload, claims.signature(), publicKeyPem);
    }

    private static void requireField(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new CommercialLicenseException("Platform license field missing: " + name);
        }
    }
}
