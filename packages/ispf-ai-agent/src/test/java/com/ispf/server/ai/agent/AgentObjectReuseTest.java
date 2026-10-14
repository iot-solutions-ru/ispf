package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentObjectReuseTest {

    @Test
    void alreadyExistsIsOkWithConfigureHintForAlert() {
        Map<String, Object> result = AgentObjectReuse.alreadyExistsOk(
                "root.platform.alert-rules.event1-event2-alert",
                "ALERT"
        );
        assertEquals("OK", result.get("status"));
        assertEquals(true, result.get("reused"));
        assertEquals("root.platform.alert-rules.event1-event2-alert", result.get("path"));
        String hint = String.valueOf(result.get("hint")).toLowerCase(Locale.ROOT);
        assertTrue(hint.contains("configure_alert"));
        assertFalse(hint.contains("get_object path="));
    }

    @Test
    void continuationAfterReuseSteersToConfigureNotGetObject() {
        String path = "root.platform.alert-rules.sum-int-float-range-alert";
        List<Map<String, Object>> steps = List.of(
                Map.of(
                        "type", "tool",
                        "tool", "create_object",
                        "arguments", Map.of(
                                "parentPath", "root.platform.alert-rules",
                                "name", "sum-int-float-range-alert",
                                "type", "ALERT"
                        ),
                        "result", AgentObjectReuse.alreadyExistsOk(path, "ALERT")
                )
        );
        String hint = AgentLoopGuard.continuationHint("create_object", steps, 96);
        assertTrue(hint.toLowerCase(Locale.ROOT).contains("configure_alert"));
        assertFalse(hint.toLowerCase(Locale.ROOT).contains("get_object + list_variables"));
    }

    @Test
    void createGetOscillationHintMentionsExistingPath() {
        String path = "root.platform.alert-rules.event1-event2-alert";
        List<Map<String, Object>> steps = List.of(
                errorCreate(path),
                getOk(path),
                errorCreate(path),
                getOk(path),
                errorCreate(path)
        );
        var block = AgentLoopGuard.checkHardBlock("get_object", Map.of("path", path), steps);
        assertTrue(block.isPresent());
        assertTrue(block.get().hint().contains(path));
        assertTrue(block.get().hint().toLowerCase(Locale.ROOT).contains("configure_alert"));
    }

    private static Map<String, Object> errorCreate(String path) {
        return Map.of(
                "type", "tool",
                "tool", "create_object",
                "arguments", Map.of("parentPath", "root.platform.alert-rules", "name", "x", "type", "ALERT"),
                "result", Map.of(
                        "status", "ERROR",
                        "error", "Object exists: " + path,
                        "existingPath", path
                )
        );
    }

    private static Map<String, Object> getOk(String path) {
        return Map.of(
                "type", "tool",
                "tool", "get_object",
                "arguments", Map.of("path", path),
                "result", Map.of("status", "OK")
        );
    }
}
