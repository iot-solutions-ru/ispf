package com.ispf.server.federation;

import com.ispf.server.config.IspfSecurityProperties;
import com.ispf.server.security.IspfSecretCipher;
import com.ispf.server.security.SecretKeySource;
import com.ispf.server.security.SecretVaultReencryptService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Service
public class FederationSecretsKeyService implements SecretKeySource {

    public enum Source {
        NONE,
        YAML,
        DATABASE
    }

    private static final int MIN_KEY_LENGTH = 16;

    private final IspfSecurityProperties securityProperties;
    private final FederationSecretsKeyStore secretsKeyStore;
    private final SecretVaultReencryptService reencryptService;

    /** In-process overlay used while rotating a DATABASE key (active = new, previous = old). */
    private volatile RotationOverlay rotationOverlay;

    public FederationSecretsKeyService(
            IspfSecurityProperties securityProperties,
            FederationSecretsKeyStore secretsKeyStore,
            @Lazy SecretVaultReencryptService reencryptService
    ) {
        this.securityProperties = securityProperties;
        this.secretsKeyStore = secretsKeyStore;
        this.reencryptService = reencryptService;
    }

    public boolean isConfigured() {
        return resolveActiveKey().isPresent();
    }

    public Source source() {
        if (hasYamlKey()) {
            return Source.YAML;
        }
        if (secretsKeyStore.findKey().isPresent()) {
            return Source.DATABASE;
        }
        return Source.NONE;
    }

    public boolean isUiConfigurable() {
        return !hasYamlKey();
    }

    public boolean isPreviousKeyConfigured() {
        return resolvePreviousKey().isPresent();
    }

    public String resolveKeyOrNull() {
        return resolveActiveKey().orElse(null);
    }

    @Override
    public Optional<String> resolveActiveKey() {
        RotationOverlay overlay = rotationOverlay;
        if (overlay != null) {
            return Optional.of(overlay.activeKey());
        }
        if (hasYamlKey()) {
            return Optional.of(securityProperties.getSecretsKey().trim());
        }
        return secretsKeyStore.findKey();
    }

    @Override
    public Optional<String> resolvePreviousKey() {
        RotationOverlay overlay = rotationOverlay;
        if (overlay != null) {
            return Optional.of(overlay.previousKey());
        }
        String previous = securityProperties.getSecretsPreviousKey();
        if (previous != null && !previous.isBlank()) {
            return Optional.of(previous.trim());
        }
        return Optional.empty();
    }

    @Override
    public String resolveActiveKeyId() {
        RotationOverlay overlay = rotationOverlay;
        if (overlay != null) {
            return overlay.activeKeyId();
        }
        return resolveActiveKey()
                .map(key -> IspfSecretCipher.normalizeConfiguredKeyId(
                        securityProperties.getSecretsKeyId(), key))
                .orElse("");
    }

    @Override
    public Optional<String> resolvePreviousKeyId() {
        RotationOverlay overlay = rotationOverlay;
        if (overlay != null) {
            return Optional.of(overlay.previousKeyId());
        }
        return resolvePreviousKey().map(key -> IspfSecretCipher.normalizeConfiguredKeyId(
                securityProperties.getSecretsPreviousKeyId(), key));
    }

    @Override
    public Optional<String> resolveKeyById(String keyId) {
        if (keyId == null || keyId.isBlank()) {
            return Optional.empty();
        }
        Optional<String> active = resolveActiveKey();
        if (active.isPresent() && keyId.equals(resolveActiveKeyId())) {
            return active;
        }
        Optional<String> previous = resolvePreviousKey();
        Optional<String> previousId = resolvePreviousKeyId();
        if (previous.isPresent() && previousId.isPresent() && keyId.equals(previousId.get())) {
            return previous;
        }
        return Optional.empty();
    }

    /**
     * Sets or rotates the UI-managed (DATABASE) secrets key. When ciphertext already exists under
     * the previous key, vault rows are re-encrypted under the new key before the store is updated.
     */
    @Transactional
    public void setUiKey(String secretsKey) {
        if (hasYamlKey()) {
            throw new IllegalStateException(
                    "ispf.security.secrets-key is already configured in application config or environment"
            );
        }
        String normalized = normalize(secretsKey);
        Optional<String> existing = secretsKeyStore.findKey();
        if (existing.isEmpty()) {
            secretsKeyStore.save(normalized);
            return;
        }
        if (Objects.equals(existing.get(), normalized)) {
            return;
        }
        String oldKey = existing.get();
        String oldId = IspfSecretCipher.normalizeConfiguredKeyId(null, oldKey);
        String newId = IspfSecretCipher.normalizeConfiguredKeyId(null, normalized);
        if (oldId.equals(newId)) {
            throw new IllegalStateException("new secrets-key collides with previous key id; choose a different key");
        }
        rotationOverlay = new RotationOverlay(normalized, newId, oldKey, oldId);
        try {
            reencryptService.reencryptAll();
            secretsKeyStore.save(normalized);
        } finally {
            rotationOverlay = null;
        }
    }

    private boolean hasYamlKey() {
        String key = securityProperties.getSecretsKey();
        return key != null && !key.isBlank();
    }

    private static String normalize(String secretsKey) {
        if (secretsKey == null || secretsKey.isBlank()) {
            throw new IllegalArgumentException("secretsKey is required");
        }
        String trimmed = secretsKey.trim();
        if (trimmed.length() < MIN_KEY_LENGTH) {
            throw new IllegalArgumentException("secretsKey must be at least " + MIN_KEY_LENGTH + " characters");
        }
        return trimmed;
    }

    private record RotationOverlay(
            String activeKey,
            String activeKeyId,
            String previousKey,
            String previousKeyId
    ) {
    }
}
