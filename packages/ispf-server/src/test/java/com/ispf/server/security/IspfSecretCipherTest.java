package com.ispf.server.security;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IspfSecretCipherTest {

    @Test
    void encryptUsesKeyedEnvelopeAndRoundTrips() {
        MutableKeySource keys = new MutableKeySource("active-secrets-key-01", null);
        IspfSecretCipher cipher = new IspfSecretCipher(keys);

        String envelope = cipher.encrypt("s3cret");
        assertThat(envelope).startsWith(IspfSecretCipher.ENVELOPE_PREFIX);
        assertThat(envelope).contains("." + keys.resolveActiveKeyId() + ".");
        assertThat(cipher.decrypt(envelope)).isEqualTo("s3cret");
        assertThat(cipher.needsReencrypt(envelope)).isFalse();
    }

    @Test
    void legacyCiphertextDecryptsWithActiveThenPrevious() {
        String oldKey = "legacy-secrets-key-aaa";
        String newKey = "rotated-secrets-key-bbb";
        String legacy = legacyEncrypt(oldKey, "peer-password");

        MutableKeySource keys = new MutableKeySource(newKey, oldKey);
        IspfSecretCipher cipher = new IspfSecretCipher(keys);

        assertThat(cipher.decrypt(legacy)).isEqualTo("peer-password");
        assertThat(cipher.needsReencrypt(legacy)).isTrue();

        String rewritten = cipher.encrypt(cipher.decrypt(legacy));
        assertThat(rewritten).startsWith(IspfSecretCipher.ENVELOPE_PREFIX);
        assertThat(cipher.needsReencrypt(rewritten)).isFalse();
        assertThat(cipher.decrypt(rewritten)).isEqualTo("peer-password");
    }

    @Test
    void envelopeBoundToPreviousKeyIdStillDecrypts() {
        String oldKey = "previous-secrets-key-xx";
        String newKey = "current-secrets-key-yy";
        String oldId = IspfSecretCipher.deriveKeyId(oldKey);
        String oldEnvelope = IspfSecretCipher.encryptWith(oldKey, oldId, "token", new SecureRandom());

        MutableKeySource keys = new MutableKeySource(newKey, oldKey);
        IspfSecretCipher cipher = new IspfSecretCipher(keys);

        assertThat(cipher.decrypt(oldEnvelope)).isEqualTo("token");
        assertThat(cipher.needsReencrypt(oldEnvelope)).isTrue();
    }

    @Test
    void unknownKeyIdFailsClosed() {
        MutableKeySource keys = new MutableKeySource("only-active-secrets-k", null);
        IspfSecretCipher cipher = new IspfSecretCipher(keys);
        String foreign = IspfSecretCipher.encryptWith(
                "foreign-secrets-key-zz",
                IspfSecretCipher.deriveKeyId("foreign-secrets-key-zz"),
                "x",
                new SecureRandom()
        );

        assertThatThrownBy(() -> cipher.decrypt(foreign))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No secrets-key available");
    }

    @Test
    void deriveKeyIdIsStable() {
        assertThat(IspfSecretCipher.deriveKeyId("same-key-material!!"))
                .isEqualTo(IspfSecretCipher.deriveKeyId("same-key-material!!"))
                .hasSize(8);
    }

    private static String legacyEncrypt(String passphrase, String plaintext) {
        try {
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(passphrase.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            cipher.init(
                    javax.crypto.Cipher.ENCRYPT_MODE,
                    new javax.crypto.spec.SecretKeySpec(keyBytes, "AES"),
                    new javax.crypto.spec.GCMParameterSpec(128, iv)
            );
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return java.util.Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static final class MutableKeySource implements SecretKeySource {
        private final AtomicReference<String> active;
        private final AtomicReference<String> previous;

        MutableKeySource(String active, String previous) {
            this.active = new AtomicReference<>(active);
            this.previous = new AtomicReference<>(previous);
        }

        @Override
        public Optional<String> resolveActiveKey() {
            return Optional.ofNullable(active.get());
        }

        @Override
        public Optional<String> resolvePreviousKey() {
            return Optional.ofNullable(previous.get());
        }

        @Override
        public String resolveActiveKeyId() {
            return resolveActiveKey().map(IspfSecretCipher::deriveKeyId).orElse("");
        }

        @Override
        public Optional<String> resolvePreviousKeyId() {
            return resolvePreviousKey().map(IspfSecretCipher::deriveKeyId);
        }

        @Override
        public Optional<String> resolveKeyById(String keyId) {
            if (keyId != null && keyId.equals(resolveActiveKeyId())) {
                return resolveActiveKey();
            }
            if (keyId != null && resolvePreviousKeyId().filter(keyId::equals).isPresent()) {
                return resolvePreviousKey();
            }
            return Optional.empty();
        }
    }
}
