package com.ispf.server.application.script;

import com.ispf.core.model.DataRecord;
import com.ispf.server.security.SignatureKeyDirectory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Verifies Ed25519 signatures over {@link FunctionSignature#message}.
 * {@code REQUIRED} checks the authenticated user. {@code DUAL} also checks a different countersigner.
 */
@Component
public class Ed25519FunctionSignatureVerifier implements FunctionSignatureVerifier {

    private final SignatureKeyDirectory keys;

    public Ed25519FunctionSignatureVerifier(SignatureKeyDirectory keys) {
        this.keys = keys;
    }

    public void enroll(String username, String publicKey) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        parsePublicKey(publicKey);
        keys.upsert(username, publicKey.trim());
    }

    @Override
    public void verify(String policy, String sourceBody, DataRecord input) {
        String signer = authenticatedUsername();
        String countersigner = text(input, "countersigner");
        boolean dual = "DUAL".equalsIgnoreCase(policy);
        if (dual) {
            if (countersigner == null || countersigner.isBlank()) {
                throw new IllegalStateException("signaturePolicy DUAL requires a countersigner");
            }
            if (countersigner.equals(signer)) {
                throw new IllegalStateException("signaturePolicy DUAL requires a second signer");
            }
        }
        byte[] message = FunctionSignature.message(policy, sourceBody, signer, dual ? countersigner : "", input);
        verifyOne(signer, text(input, "signature"), message, policy);
        if (dual) {
            verifyOne(countersigner, text(input, "countersignature"), message, policy);
        }
    }

    private void verifyOne(String username, String signatureText, byte[] message, String policy) {
        if (signatureText == null || signatureText.isBlank()) {
            throw new IllegalStateException("signaturePolicy " + policy + " requires a signature from " + username);
        }
        String stored = keys.find(username).orElseThrow(() ->
                new IllegalStateException("No signature key enrolled for " + username));
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(parsePublicKey(stored));
            verifier.update(message);
            if (!verifier.verify(Base64.getDecoder().decode(signatureText.trim()))) {
                throw new IllegalStateException("signaturePolicy " + policy + " rejected the signature from " + username);
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("signaturePolicy " + policy + " rejected the signature from " + username, ex);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("signaturePolicy " + policy + " could not verify the signature from " + username, ex);
        }
    }

    static PublicKey parsePublicKey(String publicKey) {
        if (publicKey == null || publicKey.isBlank()) {
            throw new IllegalArgumentException("publicKey is required");
        }
        try {
            byte[] encoded = Base64.getDecoder().decode(publicKey.trim());
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
        } catch (IllegalArgumentException | GeneralSecurityException ex) {
            throw new IllegalArgumentException("publicKey is not an Ed25519 SPKI key", ex);
        }
    }

    private static String authenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new IllegalStateException("signaturePolicy requires an authenticated principal");
        }
        return authentication.getName();
    }

    private static String text(DataRecord input, String field) {
        if (input == null || input.rowCount() == 0 || input.firstRow() == null) {
            return null;
        }
        Object value = input.firstRow().get(field);
        return value == null ? null : String.valueOf(value);
    }
}
