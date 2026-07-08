package com.ispf.server.application.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-183: marketplace catalog lists 10+ bundle manifest stubs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketplaceCatalogListApiTest {

    private static String marketplaceCatalogDir;

    @Autowired
    private MockMvc mockMvc;

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
        marketplaceCatalogDir = repoRoot.resolve("examples/marketplace-catalog").toString();
        registry.add("ispf.marketplace.local-bundles-dir", () -> marketplaceCatalogDir);
    }

    @BeforeEach
    void assumeCatalogPresent() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Path.of(marketplaceCatalogDir).toFile().isDirectory(),
                "examples/marketplace-catalog not found"
        );
    }

    @Test
    void listsAtLeastTenValidatedBundles() throws Exception {
        mockMvc.perform(get("/api/v1/marketplace/bundles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.source").value("local"))
                .andExpect(jsonPath("$.count").value(org.hamcrest.Matchers.greaterThanOrEqualTo(10)))
                .andExpect(jsonPath("$.bundles[0].validationStatus").value("OK"))
                .andExpect(jsonPath("$.bundles[0].slug").exists());
    }
}
