package com.ispf.server.application.bundle;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class BundleFormatVersionTest {

    @Autowired
    private BundleManifestValidator validator;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void absentFormatVersionMeansCurrent() throws Exception {
        ApplicationBundleDeployService.BundleManifest manifest = objectMapper.readValue(
                """
                        {
                          "version":"1.0.0",
                          "displayName":"Format probe",
                          "schemaName":"app_format_probe",
                          "tablePrefix":"fp_",
                          "dashboards":[{
                            "path":"root.platform.dashboards.format-probe",
                            "title":"Probe",
                            "layoutJson":"{\\"columns\\":12,\\"rowHeight\\":8,\\"widgets\\":[]}"
                          }]
                        }
                        """,
                ApplicationBundleDeployService.BundleManifest.class
        );
        assertEquals(null, manifest.formatVersion());
        assertEquals(BundleFormatVersions.CURRENT, BundleFormatVersions.resolve(manifest.formatVersion()));
        BundleValidationResult result = validator.validate("format-probe", manifest);
        assertEquals(BundleValidationResult.OK, result.status(), String.join("; ", result.errors()));
    }

    @Test
    void explicitCurrentFormatAccepted() throws Exception {
        ApplicationBundleDeployService.BundleManifest manifest = objectMapper.readValue(
                """
                        {
                          "formatVersion":1,
                          "version":"1.0.0",
                          "displayName":"Format probe",
                          "schemaName":"app_format_probe",
                          "tablePrefix":"fp_",
                          "dashboards":[{
                            "path":"root.platform.dashboards.format-probe",
                            "title":"Probe",
                            "layoutJson":"{\\"columns\\":12,\\"rowHeight\\":8,\\"widgets\\":[]}"
                          }]
                        }
                        """,
                ApplicationBundleDeployService.BundleManifest.class
        );
        assertEquals(1, manifest.formatVersion());
        BundleValidationResult result = validator.validate("format-probe-1", manifest);
        assertEquals(BundleValidationResult.OK, result.status(), String.join("; ", result.errors()));
    }

    @Test
    void unsupportedFormatRejected() throws Exception {
        ApplicationBundleDeployService.BundleManifest manifest = objectMapper.readValue(
                """
                        {
                          "formatVersion":99,
                          "version":"1.0.0",
                          "displayName":"Format probe",
                          "schemaName":"app_format_probe",
                          "tablePrefix":"fp_",
                          "dashboards":[{
                            "path":"root.platform.dashboards.format-probe",
                            "title":"Probe",
                            "layoutJson":"{\\"columns\\":12,\\"rowHeight\\":8,\\"widgets\\":[]}"
                          }]
                        }
                        """,
                ApplicationBundleDeployService.BundleManifest.class
        );
        BundleValidationResult result = validator.validate("format-probe-99", manifest);
        assertEquals(BundleValidationResult.ERROR, result.status());
        assertTrue(
                result.errors().stream().anyMatch(e -> e.contains("formatVersion")),
                () -> result.errors().toString()
        );
    }
}
