package com.ispf.server.ai.audit;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AgentAuditRedactorTest {

    @Test
    void redactsSensitiveKeysRecursively() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("path", "root.platform");
        args.put("password", "secret-value");
        args.put("config", Map.of("apiKey", "abc123", "host", "localhost"));

        Map<String, Object> redacted = AgentAuditRedactor.redactArguments(args);

        assertEquals("root.platform", redacted.get("path"));
        assertEquals("[REDACTED]", redacted.get("password"));
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) redacted.get("config");
        assertEquals("[REDACTED]", config.get("apiKey"));
        assertEquals("localhost", config.get("host"));
        assertNotEquals("secret-value", redacted.get("password"));
    }

    @Test
    void preservesNonSensitiveListValues() {
        Map<String, Object> args = Map.of(
                "tags", List.of("point", "sensor"),
                "token", "should-hide"
        );
        Map<String, Object> redacted = AgentAuditRedactor.redactArguments(args);
        assertEquals("[REDACTED]", redacted.get("token"));
        assertEquals(List.of("point", "sensor"), redacted.get("tags"));
    }
}
