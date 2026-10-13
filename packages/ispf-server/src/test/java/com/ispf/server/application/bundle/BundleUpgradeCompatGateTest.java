package com.ispf.server.application.bundle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-0064 Wave 2 lab gate: catalog pilot accepts formatVersion 1, rejects unsupported,
 * and local marketplace install succeeds on redeploy. Not cluster image N→N+1.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BundleUpgradeCompatGateTest {

    private static Path marketplaceDemoDir;
    private static Path marketplaceDemoBundle;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BundleManifestValidator validator;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void marketplaceDir(DynamicPropertyRegistry registry) {
        Path repoRoot = Path.of(System.getProperty("user.dir"));
        for (int depth = 0; depth <= 4 && !repoRoot.resolve("examples/marketplace-catalog").toFile().isDirectory(); depth++) {
            Path parent = repoRoot.getParent();
            if (parent == null) {
                break;
            }
            repoRoot = parent;
        }
        marketplaceDemoDir = repoRoot.resolve("examples/marketplace-catalog/marketplace-demo");
        marketplaceDemoBundle = marketplaceDemoDir.resolve("bundle.json");
        registry.add("ispf.marketplace.local-bundles-dir", () -> marketplaceDemoDir.toString());
    }

    @BeforeEach
    void assumeDemoBundlePresent() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Files.isRegularFile(marketplaceDemoBundle),
                "examples/marketplace-catalog/marketplace-demo/bundle.json not found"
        );
    }

    @Test
    void marketplaceDemoBundleAcceptsCurrentFormat() throws Exception {
        ApplicationBundleDeployService.BundleManifest manifest = objectMapper.readValue(
                marketplaceDemoBundle.toFile(),
                ApplicationBundleDeployService.BundleManifest.class
        );
        assertEquals(
                BundleFormatVersions.CURRENT,
                BundleFormatVersions.resolve(manifest.formatVersion()),
                "catalog pilot must resolve to CURRENT format"
        );
        BundleValidationResult result = validator.validate("marketplace-demo", manifest);
        assertEquals(BundleValidationResult.OK, result.status(), String.join("; ", result.errors()));
    }

    @Test
    void marketplaceDemoBundleRejectsUnsupportedFormat() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = objectMapper.readValue(marketplaceDemoBundle.toFile(), LinkedHashMap.class);
        raw.put("formatVersion", 99);
        ApplicationBundleDeployService.BundleManifest manifest = objectMapper.convertValue(
                raw,
                ApplicationBundleDeployService.BundleManifest.class
        );
        BundleValidationResult result = validator.validate("marketplace-demo-bad-format", manifest);
        assertEquals(BundleValidationResult.ERROR, result.status());
        assertTrue(
                result.errors().stream().anyMatch(e -> e.contains("formatVersion")),
                () -> result.errors().toString()
        );
    }

    @Test
    @WithMockUser(roles = "developer")
    void marketplaceDemoInstallThenRedeploy() throws Exception {
        mockMvc.perform(post("/api/v1/marketplace/bundles/marketplace-demo/install"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.appId").value("marketplace-demo"));

        mockMvc.perform(post("/api/v1/marketplace/bundles/marketplace-demo/install"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.appId").value("marketplace-demo"))
                .andExpect(jsonPath("$.source").value("local-marketplace"));
    }
}
