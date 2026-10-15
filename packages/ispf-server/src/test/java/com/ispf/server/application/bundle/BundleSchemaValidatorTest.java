package com.ispf.server.application.bundle;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BundleSchemaValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BundleSchemaValidator validator = new BundleSchemaValidator(objectMapper);

    @Test
    void rejectsNullManifest() {
        BundleValidationResult.Builder builder = BundleValidationResult.builder();
        validator.validate(null, builder);
        assertTrue(builder.build().issues().stream().anyMatch(i -> "BUNDLE_SCHEMA".equals(i.code())));
    }

    @Test
    void flagsObjectMissingRequiredName() {
        String json = """
                {
                  "version":"1.0.0",
                  "displayName":"Schema probe",
                  "objects":[{"parentPath":"root.platform","type":"SINGLETON"}]
                }
                """;
        ApplicationBundleDeployService.BundleManifest manifest =
                objectMapper.readValue(json, ApplicationBundleDeployService.BundleManifest.class);
        BundleValidationResult.Builder builder = BundleValidationResult.builder();
        validator.validate(manifest, builder);
        assertTrue(
                builder.build().issues().stream().anyMatch(i -> "BUNDLE_SCHEMA".equals(i.code())),
                () -> builder.build().issues().toString()
        );
    }
}
