package com.ispf.server.security;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * AES-GCM ciphertext for federation credentials and the platform vault (ADR-0049 / ADR-0065).
 *
 * <p>New writes use envelope {@code ispf1.&lt;keyId&gt;.&lt;base64(iv||ciphertext)&gt;}.
 * Legacy values (raw base64 without prefix) remain decryptable with the active key, then the previous key.
 */
@Component
public class IspfSecretCipher {

    static final String ENVELOPE_PREFIX = "ispf1.";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int DERIVED_KEY_ID_HEX_LEN = 8;

    private final SecretKeySource keySource;
    private final SecureRandom secureRandom = new SecureRandom();

    public IspfSecretCipher(SecretKeySource keySource) {
        this.keySource = keySource;
    }

    public boolean isEnabled() {
        return keySource.resolveActiveKey().isPresent();
    }

    public String activeKeyId() {
        return keySource.resolveActiveKeyId();
    }

    public boolean needsReencrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return false;
        }
        if (!ciphertext.startsWith(ENVELOPE_PREFIX)) {
            return true;
        }
        String kid = parseEnvelopeKeyId(ciphertext);
        return kid == null || !kid.equals(keySource.resolveActiveKeyId());
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        String key = keySource.resolveActiveKey()
                .orElseThrow(() -> new IllegalStateException(
                        "ispf.security.secrets-key is required to store federation credentials"));
        String keyId = keySource.resolveActiveKeyId();
        return encryptWith(key, keyId, plaintext, secureRandom);
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return null;
        }
        if (!isEnabled()) {
            throw new IllegalStateException("ispf.security.secrets-key is required to read federation credentials");
        }
        if (ciphertext.startsWith(ENVELOPE_PREFIX)) {
            return decryptEnvelope(ciphertext);
        }
        return decryptLegacy(ciphertext);
    }

    public static String encryptWith(String passphrase, String keyId, String plaintext, SecureRandom random) {
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("keyId is required");
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey(passphrase), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return ENVELOPE_PREFIX + keyId + "." + Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt secret", e);
        }
    }

    public static String deriveKeyId(String passphrase) {
        if (passphrase == null) {
            throw new IllegalArgumentException("passphrase is required");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(("ispf-kid:" + passphrase).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, DERIVED_KEY_ID_HEX_LEN);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive key id", e);
        }
    }

    public static String normalizeConfiguredKeyId(String configuredId, String passphraseFallback) {
        if (configuredId != null && !configuredId.isBlank()) {
            String trimmed = configuredId.trim();
            if (!trimmed.matches("[A-Za-z0-9._-]{1,32}")) {
                throw new IllegalArgumentException(
                        "secrets-key-id must be 1–32 chars of [A-Za-z0-9._-]");
            }
            return trimmed;
        }
        return deriveKeyId(passphraseFallback);
    }

    private String decryptEnvelope(String ciphertext) {
        String keyId = parseEnvelopeKeyId(ciphertext);
        if (keyId == null) {
            throw new IllegalStateException("Malformed secrets envelope");
        }
        String payloadB64 = ciphertext.substring(ENVELOPE_PREFIX.length() + keyId.length() + 1);
        String passphrase = keySource.resolveKeyById(keyId)
                .orElseThrow(() -> new IllegalStateException(
                        "No secrets-key available for key id '" + keyId + "'"));
        return decryptPayload(passphrase, payloadB64);
    }

    private String decryptLegacy(String ciphertext) {
        Optional<String> active = keySource.resolveActiveKey();
        if (active.isPresent()) {
            try {
                return decryptPayload(active.get(), ciphertext);
            } catch (IllegalStateException ignored) {
                // fall through to previous key
            }
        }
        Optional<String> previous = keySource.resolvePreviousKey();
        if (previous.isPresent()) {
            return decryptPayload(previous.get(), ciphertext);
        }
        throw new IllegalStateException("Failed to decrypt secret");
    }

    private static String decryptPayload(String passphrase, String payloadB64) {
        try {
            byte[] payload = Base64.getDecoder().decode(payloadB64);
            if (payload.length <= GCM_IV_LENGTH) {
                throw new IllegalStateException("Ciphertext too short");
            }
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, iv.length);
            byte[] encrypted = new byte[payload.length - iv.length];
            System.arraycopy(payload, iv.length, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey(passphrase), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt secret", e);
        }
    }

    private static String parseEnvelopeKeyId(String ciphertext) {
        if (!ciphertext.startsWith(ENVELOPE_PREFIX)) {
            return null;
        }
        int start = ENVELOPE_PREFIX.length();
        int dot = ciphertext.indexOf('.', start);
        if (dot <= start) {
            return null;
        }
        return ciphertext.substring(start, dot);
    }

    private static SecretKeySpec aesKey(String passphrase) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = digest.digest(passphrase.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(keyBytes, "AES");
    }
}
