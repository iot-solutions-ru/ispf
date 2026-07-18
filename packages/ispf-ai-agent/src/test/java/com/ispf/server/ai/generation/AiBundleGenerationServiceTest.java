package com.ispf.server.ai.generation;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiBundleGenerationServiceTest {

    private final AiBundleGenerationService service = new AiBundleGenerationService(
            null,
            null,
            null,
            null,
            null,
            new ObjectMapper()
    );

    @Test
    void extractsJsonObjectFromProseWrappedLlmResponse() throws Exception {
        String content = """
                Here is the bundle manifest:
                {
                  "version": "1.0.0",
                  "displayName": "AI generated app",
                  "schemaName": "app_ai_generated",
                  "migrations": []
                }
                """;

        assertEquals(
                "{\"version\":\"1.0.0\",\"displayName\":\"AI generated app\",\"schemaName\":\"app_ai_generated\",\"migrations\":[]}",
                service.extractJsonObject(content)
        );
    }

    @Test
    void ignoresBracesInsideJsonStrings() throws Exception {
        String content = """
                ```json
                {
                  "version": "1.0.0",
                  "displayName": "Uses {braces}",
                  "schemaName": "app_ai_generated",
                  "migrations": []
                }
                ```
                """;

        assertEquals(
                "{\"version\":\"1.0.0\",\"displayName\":\"Uses {braces}\",\"schemaName\":\"app_ai_generated\",\"migrations\":[]}",
                service.extractJsonObject(content)
        );
    }

    @Test
    void picksBestJsonObjectWhenProseContainsEmptyObjectFirst() throws Exception {
        String content = """
                {}
                {
                  "version": "1.0.0",
                  "displayName": "AI generated app",
                  "schemaName": "app_ai_generated",
                  "migrations": []
                }
                """;

        assertEquals(
                "{\"version\":\"1.0.0\",\"displayName\":\"AI generated app\",\"schemaName\":\"app_ai_generated\",\"migrations\":[]}",
                service.extractJsonObject(content)
        );
    }

    @Test
    void rejectsResponsesWithoutJsonObject() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.extractJsonObject("Here is a summary with no manifest.")
        );

        assertEquals("LLM response does not contain a JSON object", ex.getMessage());
    }
}
