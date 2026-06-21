package com.ispf.server.federation;

import com.ispf.server.config.IspfSecurityProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class FederationSecretsKeyService {

    public enum Source {
        NONE,
        YAML,
        DATABASE
    }

    private static final int MIN_KEY_LENGTH = 16;

    private final IspfSecurityProperties securityProperties;
    private final FederationSecretsKeyStore secretsKeyStore;
    private final FederationOutboundAgentStore outboundAgentStore;

    public FederationSecretsKeyService(
            IspfSecurityProperties securityProperties,
            FederationSecretsKeyStore secretsKeyStore,
            FederationOutboundAgentStore outboundAgentStore
    ) {
        this.securityProperties = securityProperties;
        this.secretsKeyStore = secretsKeyStore;
        this.outboundAgentStore = outboundAgentStore;
    }

    public boolean isConfigured() {
        return resolveKey().isPresent();
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

    public String resolveKeyOrNull() {
        return resolveKey().orElse(null);
    }

    @Transactional
    public void setUiKey(String secretsKey) {
        if (hasYamlKey()) {
            throw new IllegalStateException(
                    "ispf.security.secrets-key is already configured in application config or environment"
            );
        }
        String normalized = normalize(secretsKey);
        if (secretsKeyStore.findKey().isPresent() && !outboundAgentStore.listAll().isEmpty()) {
            throw new IllegalStateException(
                    "secrets-key is already set and outbound agents exist; delete agents before changing the key"
            );
        }
        secretsKeyStore.save(normalized);
    }

    private Optional<String> resolveKey() {
        if (hasYamlKey()) {
            return Optional.of(securityProperties.getSecretsKey().trim());
        }
        return secretsKeyStore.findKey();
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
}
