package com.ispf.server.license;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.security.KeyPair;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ispf.security.rbac-enabled=true",
        "ispf.security.token-auth-enabled=false",
        "ispf.security.local-default-role="
})
class BundleTrustRequireSignedApiTest {

    private static final KeyPair keyPair;
    private static final String publicKeyPem;

    static {
        try {
            keyPair = LicenseTestSupport.generateRsaKeyPair();
            publicKeyPem = LicenseTestSupport.toPemPublicKey(keyPair);
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void licenseProperties(DynamicPropertyRegistry registry) {
        registry.add("ispf.license.require-signed-bundles", () -> "true");
        registry.add("ispf.license.enforce", () -> "false");
        registry.add("ispf.license.public-key-pem", () -> publicKeyPem);
        registry.add("ispf.bootstrap.fixtures-enabled", () -> "false");
    }

    @Test
    void unsignedPackageImportIsForbiddenForAdmin() throws Exception {
        Map<String, Object> manifest = baseManifest();
        mockMvc.perform(post("/api/v1/platform/packages/import")
                        .header("X-ISPF-Role", "admin")
                        .param("packageId", "trust-demo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(manifest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unsignedApplicationDeployIsForbiddenForAdmin() throws Exception {
        Map<String, Object> manifest = baseManifest();
        mockMvc.perform(post("/api/v1/applications/trust-demo/deploy")
                        .header("X-ISPF-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(manifest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void operatorCannotImportUnsignedPackage() throws Exception {
        Map<String, Object> manifest = baseManifest();
        mockMvc.perform(post("/api/v1/platform/packages/import")
                        .header("X-ISPF-Role", "operator")
                        .param("packageId", "trust-demo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(manifest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedImportIsForbidden() throws Exception {
        Map<String, Object> manifest = baseManifest();
        mockMvc.perform(post("/api/v1/platform/packages/import")
                        .param("packageId", "trust-demo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(manifest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void signedPackageImportSucceedsForAdmin() throws Exception {
        String installationId = mockMvc.perform(get("/api/v1/platform/installation-id")
                        .header("X-ISPF-Role", "admin"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = objectMapper.readTree(installationId).get("installationId").asText();

        Map<String, Object> manifest = baseManifest();
        manifest.put("license", LicenseTestSupport.signedLicense(
                objectMapper, manifest, "trust-demo", id, keyPair
        ));

        mockMvc.perform(post("/api/v1/platform/packages/import")
                        .header("X-ISPF-Role", "admin")
                        .param("packageId", "trust-demo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(manifest)))
                .andExpect(status().isOk());
    }

    @Test
    void signedApplicationDeploySucceedsForAdmin() throws Exception {
        String installationId = mockMvc.perform(get("/api/v1/platform/installation-id")
                        .header("X-ISPF-Role", "admin"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = objectMapper.readTree(installationId).get("installationId").asText();

        Map<String, Object> manifest = baseManifest();
        manifest.put("license", LicenseTestSupport.signedLicense(
                objectMapper, manifest, "trust-demo", id, keyPair
        ));

        mockMvc.perform(post("/api/v1/applications/trust-demo/deploy")
                        .header("X-ISPF-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(manifest)))
                .andExpect(status().isOk());
    }

    private static Map<String, Object> baseManifest() {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("version", "1.0.0");
        manifest.put("displayName", "Trust Demo");
        manifest.put("schemaName", "app_trust_demo");
        manifest.put("migrations", java.util.List.of());
        manifest.put("functions", java.util.List.of());
        return manifest;
    }
}
