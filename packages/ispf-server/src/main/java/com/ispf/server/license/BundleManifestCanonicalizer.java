package com.ispf.server.license;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public final class BundleManifestCanonicalizer {

    private static final ObjectMapper CANONICAL_MAPPER = new ObjectMapper();

    private BundleManifestCanonicalizer() {
    }

    static String contentSha256(Object manifest, ObjectMapper objectMapper) {
        try {
            Map<String, Object> map = objectMapper.convertValue(manifest, new TypeReference<>() {});
            map.remove("license");
            String canonical = CANONICAL_MAPPER.writeValueAsString(sortRecursively(stripNulls(map)));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to canonicalize bundle manifest: " + ex.getMessage(), ex);
        }
    }

    public static String canonicalJson(Map<String, String> sortedClaims) {
        try {
            return CANONICAL_MAPPER.writeValueAsString(new TreeMap<>(sortedClaims));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize license claims: " + ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stripNulls(Map<String, Object> source) {
        Map<String, Object> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (value instanceof Map<?, ?> nested) {
                cleaned.put(entry.getKey(), stripNulls((Map<String, Object>) nested));
            } else {
                cleaned.put(entry.getKey(), value);
            }
        }
        return cleaned;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sortRecursively(Map<String, Object> source) {
        Map<String, Object> sorted = new TreeMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                sorted.put(entry.getKey(), sortRecursively((Map<String, Object>) nested));
            } else {
                sorted.put(entry.getKey(), value);
            }
        }
        return sorted;
    }
}
