package com.ispf.server.security;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Stored role lists are JSON arrays. A blank value is an empty list.
 * Unreadable JSON is an account error, not a silent empty role set.
 */
final class StoredRolesJson {

    private StoredRolesJson() {
    }

    static List<String> read(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Corrupt roles JSON", ex);
        }
    }
}
