package com.ispf.server.ai.audit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Redacts sensitive values from agent tool arguments before audit export. */
public final class AgentAuditRedactor {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password",
            "secret",
            "token",
            "apikey",
            "api_key",
            "authorization",
            "credential",
            "privatekey",
            "private_key",
            "clientsecret",
            "client_secret"
    );

    private AgentAuditRedactor() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> redactArguments(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> redacted = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            redacted.put(entry.getKey(), redactValue(entry.getKey(), entry.getValue()));
        }
        return redacted;
    }

    @SuppressWarnings("unchecked")
    private static Object redactValue(String key, Object value) {
        if (isSensitiveKey(key)) {
            return "[REDACTED]";
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String nestedKey = String.valueOf(entry.getKey());
                nested.put(nestedKey, redactValue(nestedKey, entry.getValue()));
            }
            return nested;
        }
        if (value instanceof List<?> list) {
            List<Object> redactedList = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item instanceof Map<?, ?> itemMap) {
                    Map<String, Object> nested = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : itemMap.entrySet()) {
                        String nestedKey = String.valueOf(entry.getKey());
                        nested.put(nestedKey, redactValue(nestedKey, entry.getValue()));
                    }
                    redactedList.add(nested);
                } else {
                    redactedList.add(item);
                }
            }
            return redactedList;
        }
        return value;
    }

    private static boolean isSensitiveKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        for (String sensitive : SENSITIVE_KEYS) {
            if (normalized.contains(sensitive.replace("_", ""))) {
                return true;
            }
        }
        return false;
    }
}
