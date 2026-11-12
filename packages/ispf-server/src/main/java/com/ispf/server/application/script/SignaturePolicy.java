package com.ispf.server.application.script;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataRecord;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * A function whose script declares {@code signaturePolicy} must carry a signature
 * before the engine runs the steps.
 * <p>
 * Gap: cryptographic verification of {@code signature} against the function body or
 * caller attestation is not implemented in this release; non-blank material plus an
 * authenticated principal is enforced for {@code REQUIRED} policies.
 */
public final class SignaturePolicy {

    private SignaturePolicy() {
    }

    public static void require(String sourceBody, DataRecord input) {
        String policy = readPolicy(sourceBody, new ObjectMapper());
        if (policy == null || policy.isBlank() || "NONE".equalsIgnoreCase(policy)) {
            return;
        }
        if (requiresAuthenticatedPrincipal(policy)) {
            requireAuthenticatedPrincipal(policy);
        }
        if (requiresSignatureMaterial(policy)) {
            String signature = signatureOf(input);
            if (signature == null || signature.isBlank()) {
                throw new IllegalStateException("signaturePolicy " + policy + " requires a signature");
            }
        }
    }

    private static boolean requiresAuthenticatedPrincipal(String policy) {
        return "REQUIRED".equalsIgnoreCase(policy)
                || "AUTHENTICATED".equalsIgnoreCase(policy)
                || "REQUIRE_AUTH".equalsIgnoreCase(policy);
    }

    private static boolean requiresSignatureMaterial(String policy) {
        return "REQUIRED".equalsIgnoreCase(policy);
    }

    private static void requireAuthenticatedPrincipal(String policy) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new IllegalStateException("signaturePolicy " + policy + " requires an authenticated principal");
        }
        String name = authentication.getName();
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("signaturePolicy " + policy + " requires an authenticated principal");
        }
    }

    private static String readPolicy(String sourceBody, ObjectMapper objectMapper) {
        if (sourceBody == null || !sourceBody.trim().startsWith("{")) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(sourceBody);
            JsonNode policy = root.get("signaturePolicy");
            return policy == null || policy.isNull() ? null : policy.asText();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String signatureOf(DataRecord input) {
        if (input == null || input.rowCount() == 0 || input.firstRow() == null) {
            return null;
        }
        Object value = input.firstRow().get("signature");
        return value == null ? null : String.valueOf(value);
    }
}
