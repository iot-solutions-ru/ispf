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
import java.util.List;
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
    void okSingletonHubHasNoLogicHostDevice() throws Exception {
        assertFixture("ok-singleton-hub", BundleValidationResult.OK, List.of());
    }

    @Test
    void failLogicHostDeviceIsErrorUnderStrict() throws Exception {
        assertFixture("fail-logic-host-device", BundleValidationResult.ERROR, List.of("LOGIC_HOST_DEVICE"));
    }

    private void assertFixture(String name, String expectedStatus, List<String> expectedCodes) throws Exception {
        Path fixture = fixturesRoot().resolve(name + ".json");
        assertTrue(Files.isRegularFile(fixture), "missing fixture " + fixture);
        ApplicationBundleDeployService.BundleManifest manifest =
                objectMapper.readValue(Files.readString(fixture), ApplicationBundleDeployService.BundleManifest.class);
        BundleValidationResult result = validator.validate(name, manifest, true);
        assertEquals(expectedStatus, result.status(), String.join("; ", result.errors()));

        List<String> codes = result.issues().stream().map(BundleValidationIssue::code).distinct().sorted().toList();
        List<String> wanted = expectedCodes.stream().sorted().toList();
        assertEquals(wanted, codes.stream().filter(wanted::contains).toList());

        JsonNode expectRoot = objectMapper.readTree(Files.readString(fixturesRoot().resolve("expect.json")));
        JsonNode wantedNode = expectRoot.path(name).path("codes");
        List<String> fromExpect = new ArrayList<>();
        wantedNode.forEach(n -> fromExpect.add(n.asString()));
        assertEquals(fromExpect.stream().sorted().toList(), wanted);
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
