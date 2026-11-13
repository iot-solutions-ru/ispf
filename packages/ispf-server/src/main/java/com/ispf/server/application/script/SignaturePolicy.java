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
 * {@code REQUIRED} verifies an Ed25519 signature from the authenticated user.
 * {@code DUAL} also verifies a second, different user's signature over the same bytes.
 */
public final class SignaturePolicy {

    private SignaturePolicy() {
    }

    public static String policyName(String sourceBody) {
        return readPolicy(sourceBody, new ObjectMapper());
    }

    public static void require(String sourceBody, DataRecord input) {
        require(sourceBody, input, FunctionSignatureVerifier.UNSIGNED);
    }

    public static void require(String sourceBody, DataRecord input, FunctionSignatureVerifier verifier) {
        String policy = readPolicy(sourceBody, new ObjectMapper());
        if (policy == null || policy.isBlank() || "NONE".equalsIgnoreCase(policy)) {
            return;
        }
        if (requiresAuthenticatedPrincipal(policy)) {
            requireAuthenticatedPrincipal(policy);
        }
        if (requiresSignatureMaterial(policy)) {
            verifier.verify(policy, sourceBody, input);
        }
    }

    private static boolean requiresAuthenticatedPrincipal(String policy) {
        return "REQUIRED".equalsIgnoreCase(policy)
                || "DUAL".equalsIgnoreCase(policy)
                || "AUTHENTICATED".equalsIgnoreCase(policy)
                || "REQUIRE_AUTH".equalsIgnoreCase(policy);
    }

    private static boolean requiresSignatureMaterial(String policy) {
        return "REQUIRED".equalsIgnoreCase(policy) || "DUAL".equalsIgnoreCase(policy);
    }

    private static void requireAuthenticatedPrincipal(String policy) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new IllegalStateException("signaturePolicy " + policy + " requires an authenticated principal");
        }
        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
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
}
