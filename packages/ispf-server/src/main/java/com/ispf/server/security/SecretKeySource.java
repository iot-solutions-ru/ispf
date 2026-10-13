package com.ispf.server.security;

import java.util.Optional;

/**
 * Active / previous secrets-key material for {@link IspfSecretCipher} (ADR-0065).
 */
public interface SecretKeySource {

    Optional<String> resolveActiveKey();

    Optional<String> resolvePreviousKey();

    String resolveActiveKeyId();

    Optional<String> resolvePreviousKeyId();

    Optional<String> resolveKeyById(String keyId);
}
