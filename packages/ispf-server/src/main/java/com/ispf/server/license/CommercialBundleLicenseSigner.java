package com.ispf.server.license;

import com.ispf.server.config.CommercialLicenseProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Optional vendor-side RSA signing for marketplace bundle manifests (BL-183).
 */
@Service
public class CommercialBundleLicenseSigner {

    private final CommercialLicenseProperties properties;
    private final InstallationIdService installationIdService;
    private final ObjectMapper objectMapper;
    private final Optional<BuildProperties> buildProperties;

    public CommercialBundleLicenseSigner(
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

    public boolean isConfigured() {
        String pem = properties.getSigningPrivateKeyPem();
        return pem != null && !pem.isBlank();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> signManifestIfNeeded(String appId, Object manifest) {
        Map<String, Object> root = objectMapper.convertValue(manifest, Map.class);
        if (root.get("license") != null || !isConfigured()) {
            return root;
        }
        String installationId = installationIdService.ensureInstallationId();
        String contentSha256 = BundleManifestCanonicalizer.contentSha256(root, objectMapper);
        String expiresAt = Instant.now().plus(365, ChronoUnit.DAYS).toString();
        String minPlatformVersion = com.ispf.server.platform.update.PlatformVersionSupport.currentVersion(buildProperties);
        Map<String, String> claims = new LinkedHashMap<>();
        claims.put("bundleId", appId);
        claims.put("minPlatformVersion", minPlatformVersion);
        claims.put("installationId", installationId);
        claims.put("contentSha256", contentSha256);
        claims.put("expiresAt", expiresAt);
        String payload = BundleManifestCanonicalizer.canonicalJson(claims);
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(loadPrivateKey(properties.getSigningPrivateKeyPem()));
            signature.update(payload.getBytes(StandardCharsets.UTF_8));
            Map<String, Object> license = new LinkedHashMap<>(claims);
            license.put("signature", Base64.getEncoder().encodeToString(signature.sign()));
            root.put("license", license);
            return root;
        } catch (Exception ex) {
            throw new CommercialLicenseException("Failed to sign marketplace bundle: " + ex.getMessage());
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
