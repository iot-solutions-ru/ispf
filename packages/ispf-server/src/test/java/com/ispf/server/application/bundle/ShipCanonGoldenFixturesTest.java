package com.ispf.server.application.bundle;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared ADR-0060 fixtures under {@code tools/ship-canon-fixtures/} —
 * same JSON as Node {@code ispf-cli} / marketplace ship-canon tests.
 */
@SpringBootTest
@ActiveProfiles("test")
class ShipCanonGoldenFixturesTest {

    @Autowired
    private BundleManifestValidator validator;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void allExpectJsonFixturesMatchStrictShipCanon() throws Exception {
        Path root = fixturesRoot();
        JsonNode expectRoot = objectMapper.readTree(Files.readString(root.resolve("expect.json")));
        assertTrue(expectRoot.isObject() && !expectRoot.isEmpty(), "expect.json must list fixtures");

        Iterator<Map.Entry<String, JsonNode>> fields = expectRoot.properties().iterator();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String name = entry.getKey();
            JsonNode wanted = entry.getValue();
            String strictStatus = wanted.path("strictStatus").asString();
            List<String> expectedCodes = new ArrayList<>();
            wanted.path("codes").forEach(n -> expectedCodes.add(n.asString()));

            Path fixture = root.resolve(name + ".json");
            assertTrue(Files.isRegularFile(fixture), "missing fixture " + fixture);
            ApplicationBundleDeployService.BundleManifest manifest =
                    objectMapper.readValue(
                            Files.readString(fixture),
                            ApplicationBundleDeployService.BundleManifest.class
                    );
            BundleValidationResult result = validator.validate(name, manifest, true);
            assertEquals(strictStatus, result.status(), name + ": " + String.join("; ", result.errors()));

            List<String> shipCodes = result.issues().stream()
                    .map(BundleValidationIssue::code)
                    .filter(BundleManifestValidator.SHIP_CANON_CODES::contains)
                    .distinct()
                    .sorted()
                    .toList();
            assertEquals(expectedCodes.stream().sorted().toList(), shipCodes, name);
        }
    }

    private static Path fixturesRoot() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        List<Path> candidates = List.of(
                cwd.resolve("tools/ship-canon-fixtures"),
                cwd.resolve("../tools/ship-canon-fixtures").normalize(),
                cwd.resolve("../../tools/ship-canon-fixtures").normalize()
        );
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("tools/ship-canon-fixtures not found from " + cwd);
    }
}
