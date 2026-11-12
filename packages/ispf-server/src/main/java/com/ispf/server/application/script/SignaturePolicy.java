package com.ispf.server.application.script;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataRecord;

/**
 * A function whose script declares {@code signaturePolicy} must carry a signature
 * before the engine runs the steps.
 */
public final class SignaturePolicy {

    private SignaturePolicy() {
    }

    public static void require(String sourceBody, DataRecord input) {
        String policy = readPolicy(sourceBody, new ObjectMapper());
        if (policy == null || policy.isBlank() || "NONE".equalsIgnoreCase(policy)) {
            return;
        }
        String signature = signatureOf(input);
        if (signature == null || signature.isBlank()) {
            throw new IllegalStateException("signaturePolicy " + policy + " requires a signature");
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
