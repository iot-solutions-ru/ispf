package com.ispf.server.application.bundle;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class BundleManifestJsonSupportTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void parsesWarehouseBundleFromJson() throws Exception {
        String json = new ClassPathResource("warehouse-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);

        BundleManifestJsonSupport.ParseResult parsed = BundleManifestJsonSupport.parseWithArtifact(objectMapper, json);

        assertEquals("1.0.0", parsed.manifest().version());
        assertEquals("Warehouse Reference App", parsed.manifest().displayName());
        assertTrue(parsed.artifact().containsKey("migrations"));
        assertFalse(parsed.artifact().containsKey("workflows"));
    }

    @Test
    void unwrapsNestedManifestKey() {
        BundleManifestJsonSupport.ParseResult parsed = BundleManifestJsonSupport.parseWithArtifact(
                objectMapper,
                Map.of(
                        "manifest",
                        Map.of(
                                "version", "1.0.0",
                                "displayName", "Nested",
                                "schemaName", "app_nested",
                                "migrations", java.util.List.of()
                        )
                )
        );

        assertEquals("1.0.0", parsed.manifest().version());
        assertEquals("Nested", parsed.manifest().displayName());
    }

    @Test
    void normalizesSnakeCaseKeys() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("version", "1.0.0");
        raw.put("display_name", "Snake App");
        raw.put("schema_name", "app_snake");
        raw.put("migrations", java.util.List.of(
                Map.of("id", "init", "sql", "CREATE TABLE IF NOT EXISTS demo_item (id UUID PRIMARY KEY);")
        ));
        raw.put("functions", java.util.List.of(
                Map.of(
                        "object_path", "root.platform.devices.demo-sensor-01",
                        "function_name", "demo_fn",
                        "version", "1",
                        "source", Map.of("type", "script", "body", "{\"steps\":[{\"type\":\"return\",\"fields\":{\"error_code\":\"OK\",\"error_message\":\"\"}}]}")
                )
        ));

        BundleManifestJsonSupport.ParseResult parsed = BundleManifestJsonSupport.parseWithArtifact(objectMapper, raw);

        assertEquals("Snake App", parsed.manifest().displayName());
        assertEquals("app_snake", parsed.manifest().schemaName());
        assertEquals("demo_fn", parsed.manifest().functions().getFirst().functionName());
    }

    @Test
    void rejectsEmptyManifest() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> BundleManifestJsonSupport.parse(objectMapper, Map.of())
        );
        assertEquals("Bundle manifest is empty", ex.getMessage());
    }

    @Test
    void rejectsNullVersionPlaceholders() {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("version", null);
        manifest.put("displayName", null);
        manifest.put("schemaName", null);
        manifest.put("migrations", null);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> BundleManifestJsonSupport.parse(objectMapper, manifest)
        );
        assertEquals(
                "manifest.version is required (semver, e.g. \"1.0.0\"). "
                        + "Generate a complete bundle JSON — do not return null placeholders for schema fields.",
                ex.getMessage()
        );
    }

    @Test
    void defaultBaseMapUsesAppId() {
        Map<String, Object> base = BundleManifestJsonSupport.defaultBaseMap("ai-generated");
        assertEquals("1.0.0", base.get("version"));
        assertEquals("app_ai_generated", base.get("schemaName"));
        assertEquals("Ai Generated", base.get("displayName"));
    }
}
