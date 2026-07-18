package com.ispf.server.security;

import com.ispf.server.persistence.PlatformCredentialRepository;
import com.ispf.server.persistence.entity.PlatformCredentialEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Encrypted credentials vault (ADR-0049 Wave 4). Reuses AES-GCM federation cipher.
 */
@Service
public class PlatformCredentialService {

    private final PlatformCredentialRepository repository;
    private final IspfSecretCipher cipher;
    private final ObjectMapper objectMapper;

    public PlatformCredentialService(
            PlatformCredentialRepository repository,
            IspfSecretCipher cipher,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.cipher = cipher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> upsert(String objectPath, String kind, String secretPlaintext, Map<String, Object> metadata) {
        if (objectPath == null || objectPath.isBlank()) {
            throw new IllegalArgumentException("objectPath is required");
        }
        if (secretPlaintext == null) {
            throw new IllegalArgumentException("secret is required");
        }
        PlatformCredentialEntity existing = repository.findByObjectPath(objectPath).orElseGet(() -> {
            PlatformCredentialEntity entity = new PlatformCredentialEntity();
            entity.setId(UUID.randomUUID().toString());
            entity.setObjectPath(objectPath);
            entity.setCreatedAt(Instant.now());
            return entity;
        });
        existing.setKind(kind == null || kind.isBlank() ? "generic" : kind);
        existing.setCipherText(cipher.encrypt(secretPlaintext));
        try {
            existing.setMetadataJson(objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata));
        } catch (Exception e) {
            existing.setMetadataJson("{}");
        }
        existing.setUpdatedAt(Instant.now());
        repository.save(existing);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "OK");
        result.put("objectPath", objectPath);
        result.put("kind", existing.getKind());
        result.put("updatedAt", existing.getUpdatedAt().toString());
        return result;
    }

    @Transactional(readOnly = true)
    public Optional<String> resolveSecret(String objectPath) {
        return repository.findByObjectPath(objectPath).map(entity -> cipher.decrypt(entity.getCipherText()));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> describe(String objectPath) {
        return repository.findByObjectPath(objectPath)
                .map(entity -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("objectPath", entity.getObjectPath());
                    row.put("kind", entity.getKind());
                    row.put("metadataJson", entity.getMetadataJson());
                    row.put("updatedAt", entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().toString());
                    row.put("hasSecret", entity.getCipherText() != null && !entity.getCipherText().isBlank());
                    return row;
                })
                .orElse(Map.of("status", "NOT_FOUND", "objectPath", objectPath));
    }
}
