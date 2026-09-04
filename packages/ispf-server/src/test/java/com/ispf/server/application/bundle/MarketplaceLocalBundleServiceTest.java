package com.ispf.server.application.bundle;

import com.ispf.server.config.MarketplaceProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.LinkedHashMap;
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
        Path demoDir = resolveMarketplaceDemoDir();
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
        when(bundleDeployService.supportsOperatorUi(eq("marketplace-demo"))).thenReturn(true);

        Map<String, Object> installed = service.installLocalBundle("marketplace-demo");
        assertEquals("OK", installed.get("status"));
        assertEquals("marketplace-demo", installed.get("appId"));
        assertEquals(true, installed.get("operatorReady"));
    }

    @Test
    void installLocalBundlePreservesPartialDeployStatus() throws Exception {
        Path demoDir = resolveMarketplaceDemoDir();
        org.junit.jupiter.api.Assumptions.assumeTrue(demoDir.toFile().isDirectory(), "examples/marketplace-demo not found");

        MarketplaceProperties properties = new MarketplaceProperties();
        properties.setLocalBundlesDir(demoDir.toString());
        MarketplaceLocalBundleService service = new MarketplaceLocalBundleService(
                properties, new ObjectMapper(), bundleDeployService
        );

        Map<String, Object> deployResult = new LinkedHashMap<>();
        deployResult.put("status", "PARTIAL");
        deployResult.put("errors", List.of("dashboard:x: boom"));
        deployResult.put("failedSteps", List.of("dashboard:x: boom"));
        deployResult.put("applied", List.of("register"));
        when(bundleDeployService.deploy(eq("marketplace-demo"), any())).thenReturn(deployResult);
        when(bundleDeployService.supportsOperatorUi(eq("marketplace-demo"))).thenReturn(false);

        Map<String, Object> installed = service.installLocalBundle("marketplace-demo");
        assertEquals("PARTIAL", installed.get("status"));
        assertEquals(List.of("dashboard:x: boom"), installed.get("errors"));
        assertEquals("local-marketplace", installed.get("source"));
        assertEquals(false, installed.get("operatorReady"));
    }

    @Test
    void installLocalBundlePreservesFailedDeployStatus() throws Exception {
        Path demoDir = resolveMarketplaceDemoDir();
        org.junit.jupiter.api.Assumptions.assumeTrue(demoDir.toFile().isDirectory(), "examples/marketplace-demo not found");

        MarketplaceProperties properties = new MarketplaceProperties();
        properties.setLocalBundlesDir(demoDir.toString());
        MarketplaceLocalBundleService service = new MarketplaceLocalBundleService(
                properties, new ObjectMapper(), bundleDeployService
        );

        Map<String, Object> deployResult = new LinkedHashMap<>();
        deployResult.put("status", "FAILED");
        deployResult.put("errors", List.of("register: boom"));
        deployResult.put("failedSteps", List.of("register: boom"));
        deployResult.put("applied", List.of());
        when(bundleDeployService.deploy(eq("marketplace-demo"), any())).thenReturn(deployResult);
        when(bundleDeployService.supportsOperatorUi(eq("marketplace-demo"))).thenReturn(false);

        Map<String, Object> installed = service.installLocalBundle("marketplace-demo");
        assertEquals("FAILED", installed.get("status"));
        assertEquals(List.of("register: boom"), installed.get("failedSteps"));
    }

    private static Path resolveMarketplaceDemoDir() {
        Path repoRoot = Path.of(System.getProperty("user.dir"));
        for (int depth = 0; depth <= 4 && !repoRoot.resolve("examples/marketplace-demo").toFile().isDirectory(); depth++) {
            Path parent = repoRoot.getParent();
            if (parent == null) {
                break;
            }
            repoRoot = parent;
        }
        return repoRoot.resolve("examples/marketplace-demo");
    }
}
