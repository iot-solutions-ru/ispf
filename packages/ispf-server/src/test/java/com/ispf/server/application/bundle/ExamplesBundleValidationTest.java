package com.ispf.server.application.bundle;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
@ActiveProfiles("test")
class ExamplesBundleValidationTest {

    @Autowired
    private BundleManifestValidator validator;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void allExampleBundlesValidate() throws Exception {
        Path examplesRoot = resolveExamplesRoot();
        assertTrue(Files.isDirectory(examplesRoot), "examples/ not found from " + Path.of("").toAbsolutePath());

        List<Path> bundles;
        try (Stream<Path> walk = Files.walk(examplesRoot)) {
            bundles = walk
                    .filter(p -> p.getFileName().toString().equals("bundle.json"))
                    .filter(p -> !p.toString().contains("node_modules"))
                    .sorted()
                    .toList();
        }
        assertTrue(bundles.size() >= 10, "expected many example bundles, found " + bundles.size());

        List<String> failures = new ArrayList<>();
        for (Path bundlePath : bundles) {
            String json = Files.readString(bundlePath);
            ApplicationBundleDeployService.BundleManifest manifest =
                    objectMapper.readValue(json, ApplicationBundleDeployService.BundleManifest.class);
            String appId = inferAppId(bundlePath, manifest);
            BundleValidationResult result = validator.validate(appId, manifest);
            List<String> hardErrors = new ArrayList<>();
            if (result.issues() != null) {
                for (BundleValidationIssue issue : result.issues()) {
                    if (!BundleValidationIssue.ERROR.equals(issue.severity())) {
                        continue;
                    }
                    String code = issue.code() != null ? issue.code() : "";
                    if (code.equals("LOGIC_HOST_DEVICE")
                            || code.equals("BUNDLE_SCHEMA")
                            || code.startsWith("BINDING_")) {
                        hardErrors.add("[" + code + "] " + issue.path() + ": " + issue.message());
                    }
                }
            }
            // Also catch plain LOGIC_HOST errors if issues empty but errors list has them
            for (String err : result.errors()) {
                if (err != null && err.contains("LOGIC_HOST_DEVICE")) {
                    hardErrors.add(err);
                }
            }
            if (!hardErrors.isEmpty()) {
                failures.add(bundlePath + " [" + appId + "]: " + String.join("; ", hardErrors));
            }
        }
        if (!failures.isEmpty()) {
            fail("Example bundle validation failures (" + failures.size() + "):\n" + String.join("\n", failures));
        }
    }

    @Test
    void rejectsDeviceHostedFunction() throws Exception {
        String json = """
                {
                  "version":"1.0.0",
                  "displayName":"Bad",
                  "tablePrefix":"b_",
                  "schemaName":"app_bad",
                  "objects":[{"parentPath":"root.platform.devices","name":"bad-hub","type":"DEVICE"}],
                  "functions":[{
                    "objectPath":"root.platform.devices.bad-hub",
                    "functionName":"listItems",
                    "source":{"type":"script","body":"{\\"steps\\":[{\\"type\\":\\"return\\",\\"fields\\":{\\"error_code\\":\\"OK\\"}}]}"}
                  }]
                }
                """;
        ApplicationBundleDeployService.BundleManifest manifest =
                objectMapper.readValue(json, ApplicationBundleDeployService.BundleManifest.class);
        BundleValidationResult result = validator.validate("bad", manifest);
        assertEquals(BundleValidationResult.ERROR, result.status());
        assertTrue(
                result.issues().stream().anyMatch(i -> "LOGIC_HOST_DEVICE".equals(i.code())),
                String.join("; ", result.errors())
        );
    }

    private static Path resolveExamplesRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        Path[] candidates = {
                cwd.resolve("examples"),
                cwd.resolve("../../examples").normalize(),
                cwd.getParent() != null ? cwd.getParent().resolve("examples") : null
        };
        for (Path candidate : candidates) {
            if (candidate != null && Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return cwd.resolve("examples");
    }

    private static String inferAppId(Path bundlePath, ApplicationBundleDeployService.BundleManifest manifest) {
        Path parent = bundlePath.getParent();
        if (parent != null) {
            String name = parent.getFileName().toString();
            if (!"examples".equals(name) && !"marketplace-catalog".equals(name)) {
                return name.replace("-ui", "");
            }
        }
        if (manifest.schemaName() != null && !manifest.schemaName().isBlank()) {
            return manifest.schemaName().replaceFirst("^app_", "").replace('_', '-');
        }
        return "example";
    }
}
