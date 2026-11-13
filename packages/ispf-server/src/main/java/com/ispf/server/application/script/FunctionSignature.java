package com.ispf.server.application.script;

import com.ispf.core.model.DataRecord;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Bytes both signers cover. The countersigner name is inside the message, so swapping it
 * invalidates both signatures.
 */
public final class FunctionSignature {

    static final Set<String> EXCLUDED_FIELDS = Set.of("signature", "countersignature", "countersigner");

    private FunctionSignature() {
    }

    public static byte[] message(String policy, String sourceBody, String signer, String countersigner, DataRecord input) {
        StringBuilder builder = new StringBuilder();
        builder.append("v1\n");
        builder.append(policy == null ? "" : policy.trim().toUpperCase(Locale.ROOT)).append('\n');
        builder.append(signer == null ? "" : signer).append('\n');
        builder.append(countersigner == null ? "" : countersigner).append('\n');
        builder.append(sha256Hex(sourceBody)).append('\n');
        if (input != null && input.rowCount() > 0 && input.firstRow() != null) {
            Map<String, String> fields = new TreeMap<>();
            input.firstRow().forEach((key, value) -> {
                if (key != null && !EXCLUDED_FIELDS.contains(key)) {
                    fields.put(key, value == null ? "" : String.valueOf(value));
                }
            });
            fields.forEach((key, value) -> builder.append(key).append('=').append(value).append('\n'));
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static String sha256Hex(String sourceBody) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((sourceBody == null ? "" : sourceBody).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
