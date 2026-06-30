package com.ispf.server.license;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RSA public key parsing and signature verification for commercial licenses.
 * Supports multiple PEM blocks in one config value (key rotation grace period).
 */
public final class LicensePublicKeySupport {

    private static final Pattern PEM_BLOCK = Pattern.compile(
            "-----BEGIN PUBLIC KEY-----\\s*([A-Za-z0-9+/=\\s]+?)\\s*-----END PUBLIC KEY-----",
            Pattern.DOTALL
    );

    private LicensePublicKeySupport() {
    }

    public static List<PublicKey> parsePublicKeys(String pemConfig) {
        if (pemConfig == null || pemConfig.isBlank()) {
            return List.of();
        }
        List<PublicKey> keys = new ArrayList<>();
        Matcher matcher = PEM_BLOCK.matcher(pemConfig);
        while (matcher.find()) {
            keys.add(decodePublicKey(matcher.group(1)));
        }
        if (keys.isEmpty()) {
            keys.add(decodePublicKey(normalizeSinglePem(pemConfig)));
        }
        return List.copyOf(keys);
    }

    public static void verifyRsaSha256(String payload, String signatureBase64, String publicKeyPem) {
        List<PublicKey> keys = parsePublicKeys(publicKeyPem);
        if (keys.isEmpty()) {
            throw new CommercialLicenseException("ispf.license.public-key-pem is not configured");
        }
        byte[] signatureBytes;
        try {
            signatureBytes = Base64.getDecoder().decode(signatureBase64);
        } catch (IllegalArgumentException ex) {
            throw new CommercialLicenseException("License signature is not valid Base64");
        }
        CommercialLicenseException lastFailure = null;
        for (PublicKey publicKey : keys) {
            try {
                Signature signature = Signature.getInstance("SHA256withRSA");
                signature.initVerify(publicKey);
                signature.update(payload.getBytes(StandardCharsets.UTF_8));
                if (signature.verify(signatureBytes)) {
                    return;
                }
                lastFailure = new CommercialLicenseException("License signature invalid");
            } catch (CommercialLicenseException ex) {
                lastFailure = ex;
            } catch (Exception ex) {
                lastFailure = new CommercialLicenseException("License signature verify error: " + ex.getMessage());
            }
        }
        throw lastFailure != null
                ? lastFailure
                : new CommercialLicenseException("License signature invalid");
    }

    private static PublicKey decodePublicKey(String base64Body) {
        try {
            String normalized = base64Body.replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(normalized);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
        } catch (Exception ex) {
            throw new CommercialLicenseException("Invalid RSA public key PEM: " + ex.getMessage());
        }
    }

    private static String normalizeSinglePem(String pem) {
        return pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
    }
}
