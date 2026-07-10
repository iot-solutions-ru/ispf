package com.ispf.server.application.bundle;

import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.platform.analytics.pack.DropInAnalyticsPackLoader;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BL-96: installed solutions catalog (applications + analytics packs).
 * Marketplace listings install via {@link MarketplaceService}.
 */
@Service
public class SolutionCatalogService {

    private final ApplicationDataStore dataStore;
    private final ApplicationBundleSnapshotStore snapshotStore;
    private final ApplicationBundleDeployService deployService;
    private final DropInAnalyticsPackLoader analyticsPackLoader;
    private final ObjectMapper objectMapper;

    public SolutionCatalogService(
            ApplicationDataStore dataStore,
            ApplicationBundleSnapshotStore snapshotStore,
            ApplicationBundleDeployService deployService,
            DropInAnalyticsPackLoader analyticsPackLoader,
            ObjectMapper objectMapper
    ) {
        this.dataStore = dataStore;
        this.snapshotStore = snapshotStore;
        this.deployService = deployService;
        this.analyticsPackLoader = analyticsPackLoader;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> catalog() {
        Map<String, Object> response = new LinkedHashMap<>();
        List<Map<String, Object>> installed = new ArrayList<>();
        for (Map<String, Object> appRow : dataStore.listAllApps()) {
            String appId = String.valueOf(appRow.get("app_id"));
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("appId", appId);
            entry.put("displayName", appRow.get("display_name"));
            entry.put("schemaName", appRow.get("schema_name"));
            entry.put("createdAt", appRow.get("created_at"));
            entry.put("versions", snapshotStore.listHistory(appId));
            snapshotStore.findActive(appId).ifPresent(active -> {
                entry.put("activeVersion", active.bundleVersion());
                entry.put("deployedAt", active.deployedAt().toString());
                enrichFromManifest(entry, active.manifestJson());
            });
            installed.add(entry);
        }
        response.put("installed", installed);
        response.put("installedAnalyticsPacks", analyticsPackLoader.listInstalledPacks());
        return response;
    }

    public Map<String, Object> uninstallApplication(String appId) throws Exception {
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("appId is required");
        }
        String normalized = appId.trim();
        if (dataStore.findApp(normalized).isEmpty()) {
            throw new IllegalArgumentException("Application is not installed: " + normalized);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("appId", normalized);
        result.put("action", "uninstall");
        if (snapshotStore.findActive(normalized).isPresent()) {
            Map<String, Object> removed = deployService.removeBundleObjects(normalized);
            result.putAll(removed);
        } else {
            result.put("status", "OK");
            result.put("removed", List.of());
            result.put("skipped", List.of());
            result.put("errors", List.of());
        }
        snapshotStore.deactivateAll(normalized);
        dataStore.deleteApp(normalized);
        return result;
    }

    public Map<String, Object> uninstallAnalyticsPack(String packId) throws Exception {
        return analyticsPackLoader.uninstallPack(packId);
    }

    @SuppressWarnings("unchecked")
    private void enrichFromManifest(Map<String, Object> entry, String manifestJson) {
        try {
            Map<String, Object> manifest = objectMapper.readValue(manifestJson, Map.class);
            entry.put("changelog", manifest.getOrDefault("changelog",
                    manifest.get("metadata") instanceof Map<?, ?> meta ? meta.get("changelog") : null));
            int screens = 0;
            if (manifest.get("dashboards") instanceof List<?> dashboards) {
                screens += dashboards.size();
            }
            if (manifest.get("operatorUi") instanceof Map<?, ?> operatorUi
                    && operatorUi.get("dashboards") instanceof List<?> opDash) {
                screens += opDash.size();
            }
            entry.put("screenCount", screens);
            if (manifest.get("displayName") != null) {
                entry.put("bundleDisplayName", manifest.get("displayName"));
            }
        } catch (Exception ignored) {
            // keep catalog resilient
        }
    }
}
