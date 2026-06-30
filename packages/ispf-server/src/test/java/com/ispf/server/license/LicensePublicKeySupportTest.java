package com.ispf.server.license;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LicensePublicKeySupportTest {

    @Test
    void acceptsSignatureFromAnyConfiguredPublicKey() throws Exception {
        KeyPair primary = LicenseTestSupport.generateRsaKeyPair();
        KeyPair secondary = LicenseTestSupport.generateRsaKeyPair();
        String payload = "{\"tier\":\"enterprise\"}";
        String signature = sign(payload, secondary);

        String pem = LicenseTestSupport.toPemPublicKey(primary) + "\n" + LicenseTestSupport.toPemPublicKey(secondary);
        assertDoesNotThrow(() -> LicensePublicKeySupport.verifyRsaSha256(payload, signature, pem));
    }

    @Test
    void rejectsSignatureWhenNoConfiguredKeyMatches() throws Exception {
        KeyPair primary = LicenseTestSupport.generateRsaKeyPair();
        KeyPair other = LicenseTestSupport.generateRsaKeyPair();
        String payload = "{\"tier\":\"enterprise\"}";
        String signature = sign(payload, other);

        assertThrows(
                CommercialLicenseException.class,
                () -> LicensePublicKeySupport.verifyRsaSha256(payload, signature, LicenseTestSupport.toPemPublicKey(primary))
        );
    }

    private static String sign(String payload, KeyPair keyPair) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }
}
