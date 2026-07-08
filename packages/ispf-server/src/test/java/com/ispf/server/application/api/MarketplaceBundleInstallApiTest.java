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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-183: local marketplace install endpoint validates manifest and deploys bundle.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketplaceBundleInstallApiTest {

    private static String marketplaceDemoDir;

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void marketplaceDir(DynamicPropertyRegistry registry) {
        Path repoRoot = Path.of(System.getProperty("user.dir"));
        for (int depth = 0; depth <= 4 && !repoRoot.resolve("examples/marketplace-demo").toFile().isDirectory(); depth++) {
            Path parent = repoRoot.getParent();
            if (parent == null) {
                break;
            }
            repoRoot = parent;
        }
        marketplaceDemoDir = repoRoot.resolve("examples/marketplace-demo").toString();
        registry.add("ispf.marketplace.local-bundles-dir", () -> marketplaceDemoDir);
    }

    @BeforeEach
    void assumeDemoBundlePresent() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Path.of(marketplaceDemoDir).resolve("bundle.json").toFile().isFile(),
                "examples/marketplace-demo not found"
        );
    }

    @Test
    void installMarketplaceDemoBundleBySlug() throws Exception {
        mockMvc.perform(post("/api/v1/marketplace/bundles/marketplace-demo/install"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.appId").value("marketplace-demo"))
                .andExpect(jsonPath("$.source").value("local-marketplace"));
    }
}
