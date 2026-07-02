package com.ispf.server.application.bundle;

import com.ispf.server.application.data.ApplicationDataStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * BL-96: published bundle catalog + reference example install.
 */
@Service
public class SolutionCatalogService {

    private static final List<ReferenceExample> REFERENCE_EXAMPLES = List.of(
            new ReferenceExample(
                    "mes-reference",
                    "mes-reference",
                    "MES Reference",
                    "Dispatch orders, filling lifecycle, operator journal",
                    "mes-reference-bundle.json"
            ),
            new ReferenceExample(
                    "warehouse-app",
                    "warehouse",
                    "Warehouse Reference",
                    "Storage locations list via BFF",
                    "warehouse-bundle.json"
            ),
            new ReferenceExample(
                    "building-hvac-app",
                    "building-hvac",
                    "Building HVAC Reference",
                    "Zone comfort setpoints and Haystack-tagged points",
                    "building-hvac-bundle.json"
            )
    );

    private final ApplicationDataStore dataStore;
    private final ApplicationBundleSnapshotStore snapshotStore;
    private final ApplicationBundleDeployService deployService;
    private final ObjectMapper objectMapper;

    public SolutionCatalogService(
            ApplicationDataStore dataStore,
            ApplicationBundleSnapshotStore snapshotStore,
            ApplicationBundleDeployService deployService,
            ObjectMapper objectMapper
    ) {
        this.dataStore = dataStore;
        this.snapshotStore = snapshotStore;
        this.deployService = deployService;
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
        response.put("referenceExamples", referenceExamples());
        return response;
    }

    public Map<String, Object> installReferenceExample(String exampleId) {
        ReferenceExample example = findReference(exampleId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown reference example: " + exampleId));
        try {
            String json = loadClasspathBundle(example.resourceName());
            ApplicationBundleDeployService.BundleManifest manifest =
                    BundleManifestJsonSupport.parse(objectMapper, json);
            Map<String, Object> deployResult = deployService.deploy(example.appId(), manifest);
            Map<String, Object> result = new LinkedHashMap<>(deployResult);
            result.put("exampleId", example.exampleId());
            result.put("installedFrom", "reference");
            return result;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to install reference example " + exampleId + ": " + ex.getMessage(), ex);
        }
    }

    private List<Map<String, Object>> referenceExamples() {
        return REFERENCE_EXAMPLES.stream().map(example -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("exampleId", example.exampleId());
            row.put("appId", example.appId());
            row.put("title", example.title());
            row.put("description", example.description());
            row.put("installed", dataStore.findApp(example.appId()).isPresent());
            snapshotStore.findActive(example.appId()).ifPresent(active ->
                    row.put("activeVersion", active.bundleVersion())
            );
            return row;
        }).toList();
    }

    private Optional<ReferenceExample> findReference(String exampleId) {
        if (exampleId == null || exampleId.isBlank()) {
            return Optional.empty();
        }
        String normalized = exampleId.trim();
        return REFERENCE_EXAMPLES.stream()
                .filter(item -> item.exampleId().equals(normalized) || item.appId().equals(normalized))
                .findFirst();
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

    private static String loadClasspathBundle(String resourceName) throws Exception {
        return new ClassPathResource(resourceName).getContentAsString(StandardCharsets.UTF_8);
    }

    private record ReferenceExample(
            String exampleId,
            String appId,
            String title,
            String description,
            String resourceName
    ) {
    }
}
