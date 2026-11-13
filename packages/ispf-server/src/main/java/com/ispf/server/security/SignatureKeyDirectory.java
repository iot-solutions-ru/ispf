package com.ispf.server.security;

import java.util.Optional;

/** Enrolled Ed25519 public keys, keyed by username. */
public interface SignatureKeyDirectory {

    void upsert(String username, String publicKey);

    Optional<String> find(String username);

    void delete(String username);
}
