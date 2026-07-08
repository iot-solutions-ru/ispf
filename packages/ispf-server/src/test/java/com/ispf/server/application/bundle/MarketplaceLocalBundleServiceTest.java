package com.ispf.server.application.bundle;

import com.ispf.server.config.MarketplaceProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketplaceLocalBundleServiceTest {

    @Mock
    private ApplicationBundleDeployService bundleDeployService;

    @Test
    void listsMarketplaceDemoBundleWithValidManifest() throws Exception {
        Path repoRoot = Path.of(System.getProperty("user.dir"));
        for (int depth = 0; depth <= 4 && !repoRoot.resolve("examples/marketplace-demo").toFile().isDirectory(); depth++) {
            Path parent = repoRoot.getParent();
            if (parent == null) {
                break;
            }
            repoRoot = parent;
        }
        Path demoDir = repoRoot.resolve("examples/marketplace-demo");
        org.junit.jupiter.api.Assumptions.assumeTrue(demoDir.toFile().isDirectory(), "examples/marketplace-demo not found");

        MarketplaceProperties properties = new MarketplaceProperties();
        properties.setLocalBundlesDir(demoDir.toString());
        MarketplaceLocalBundleService service = new MarketplaceLocalBundleService(
                properties, new ObjectMapper(), bundleDeployService
        );

        Map<String, Object> response = service.listLocalBundles();
        assertEquals("OK", response.get("status"));
        assertEquals(1, response.get("count"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bundles = (List<Map<String, Object>>) response.get("bundles");
        assertEquals("OK", bundles.get(0).get("validationStatus"));
        assertEquals("marketplace-demo", bundles.get(0).get("slug"));
        assertTrue(bundles.get(0).containsKey("displayName"));

        when(bundleDeployService.deploy(eq("marketplace-demo"), any()))
                .thenReturn(Map.of("deployed", true));

        Map<String, Object> installed = service.installLocalBundle("marketplace-demo");
        assertEquals("OK", installed.get("status"));
        assertEquals("marketplace-demo", installed.get("appId"));
    }
}
