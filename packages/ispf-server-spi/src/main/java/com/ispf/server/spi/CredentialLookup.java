package com.ispf.server.spi;

import java.util.Map;
import java.util.Optional;

/**
 * Read-only view of a credential vault entry. Implemented by the platform credential store.
 */
public interface CredentialLookup {

    Optional<String> resolveSecret(String objectPath);

    Map<String, Object> describe(String objectPath);
}
