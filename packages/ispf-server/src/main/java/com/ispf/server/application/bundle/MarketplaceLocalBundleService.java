package com.ispf.server.application.bundle;

import com.ispf.server.config.MarketplaceProperties;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * BL-183: local marketplace bundle listing from repo examples (dev/lab skeleton).
 */
@Service
public class MarketplaceLocalBundleService {

    private static final List<String> LISTING_REQUIRED = List.of(
            "slug", "title", "description", "appId", "latestVersion"
    );
    private static final List<String> BUNDLE_REQUIRED = List.of("version", "displayName");

    private final MarketplaceProperties properties;
    private final ObjectMapper objectMapper;
    private final ApplicationBundleDeployService bundleDeployService;

    public MarketplaceLocalBundleService(
            MarketplaceProperties properties,
            ObjectMapper objectMapper,
            ApplicationBundleDeployService bundleDeployService
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.bundleDeployService = bundleDeployService;
    }

    public Map<String, Object> listLocalBundles() {
        Path root = resolveLocalRoot();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("source", "local");
        response.put("root", root != null ? root.toString() : "");
        if (root == null || !Files.isDirectory(root)) {
            response.put("status", "OK");
            response.put("count", 0);
            response.put("bundles", List.of());
            return response;
        }

        List<Map<String, Object>> bundles = new ArrayList<>();
        try {
            if (Files.isRegularFile(root.resolve("listing.manifest.json"))) {
                bundles.add(loadBundleListing(root));
            } else {
                try (Stream<Path> dirs = Files.list(root)) {
                    for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
                        bundles.add(loadBundleListing(dir));
                    }
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to scan marketplace bundles: " + ex.getMessage(), ex);
        }

        response.put("status", "OK");
        response.put("count", bundles.size());
        response.put("bundles", bundles);
        return response;
    }

    /**
     * BL-183: validate listing + bundle manifest, then deploy via {@link ApplicationBundleDeployService}.
     *
     * @param bundleId slug from listing.manifest.json or bundle directory name
     */
    public Map<String, Object> installLocalBundle(String bundleId) throws Exception {
        if (bundleId == null || bundleId.isBlank()) {
            throw new IllegalArgumentException("bundle id is required");
        }
        Path root = resolveLocalRoot();
        if (root == null || !Files.isDirectory(root)) {
            throw new IllegalStateException("Local marketplace bundle directory is not configured");
        }

        ResolvedListing resolved = resolveListing(root, bundleId.trim());
        if (!"OK".equals(resolved.row().get("validationStatus"))) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("status", "ERROR");
            error.put("bundleId", bundleId);
            error.put("validationStatus", resolved.row().get("validationStatus"));
            error.put("errors", resolved.row().getOrDefault("errors", List.of()));
            return error;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> listing = objectMapper.readValue(Files.readString(resolved.listingPath()), Map.class);
        String pricing = stringValue(listing.getOrDefault("pricing", "free"));
        if (!"free".equalsIgnoreCase(pricing)) {
            throw new IllegalArgumentException("Local install supports free bundles only — use paid activation flow");
        }

        String manifestJson = Files.readString(resolved.bundlePath());
        String appId = stringValue(listing.get("appId"));
        if (appId.isBlank()) {
            throw new IllegalStateException("Listing has no appId");
        }

        ApplicationBundleDeployService.BundleManifest manifest =
                BundleManifestJsonSupport.parse(objectMapper, manifestJson);
        Map<String, Object> deployResult = bundleDeployService.deploy(appId, manifest);

        Map<String, Object> result = new LinkedHashMap<>(deployResult);
        result.put("status", "OK");
        result.put("source", "local-marketplace");
        result.put("bundleId", bundleId);
        result.put("slug", listing.get("slug"));
        result.put("appId", appId);
        result.put("version", listing.get("latestVersion"));
        result.put("listingPath", resolved.listingPath().toString());
        result.put("bundlePath", resolved.bundlePath().toString());
        boolean operatorReady = bundleDeployService.supportsOperatorUi(appId);
        result.put("operatorReady", operatorReady);
        if (!operatorReady) {
            result.put(
                    "operatorReadyHint",
                    "Bundle installed without Operator dashboards/reports. Reinstall a full package or configure root.platform.operator-apps."
            );
        }
        return result;
    }

    /**
     * BL-183: uninstall a locally installed marketplace bundle by removing deployed tree artifacts.
     */
    public Map<String, Object> uninstallLocalBundle(String bundleId) throws Exception {
        if (bundleId == null || bundleId.isBlank()) {
            throw new IllegalArgumentException("bundle id is required");
        }
        Path root = resolveLocalRoot();
        if (root == null || !Files.isDirectory(root)) {
            throw new IllegalStateException("Local marketplace bundle directory is not configured");
        }

        ResolvedListing resolved = resolveListing(root, bundleId.trim());
        if (!"OK".equals(resolved.row().get("validationStatus"))) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("status", "ERROR");
            error.put("bundleId", bundleId);
            error.put("validationStatus", resolved.row().get("validationStatus"));
            error.put("errors", resolved.row().getOrDefault("errors", List.of()));
            return error;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> listing = objectMapper.readValue(Files.readString(resolved.listingPath()), Map.class);
        String appId = stringValue(listing.get("appId"));
        if (appId.isBlank()) {
            throw new IllegalStateException("Listing has no appId");
        }

        Map<String, Object> removeResult = bundleDeployService.removeBundleObjects(appId);
        Map<String, Object> result = new LinkedHashMap<>(removeResult);
        result.put("status", "OK".equals(removeResult.get("status")) ? "OK" : removeResult.get("status"));
        result.put("source", "local-marketplace");
        result.put("action", "uninstall");
        result.put("bundleId", bundleId);
        result.put("slug", listing.get("slug"));
        result.put("appId", appId);
        result.put("version", listing.get("latestVersion"));
        return result;
    }

    private record ResolvedListing(Map<String, Object> row, Path listingPath, Path bundlePath) {
    }

    private ResolvedListing resolveListing(Path root, String bundleId) throws Exception {
        if (Files.isRegularFile(root.resolve("listing.manifest.json"))) {
            Map<String, Object> row = loadBundleListing(root);
            String slug = stringValue(row.get("slug"));
            String directory = stringValue(row.get("directory"));
            if (bundleId.equals(slug) || bundleId.equals(directory)) {
                return new ResolvedListing(
                        row,
                        root.resolve("listing.manifest.json"),
                        root.resolve("bundle.json")
                );
            }
            throw new IllegalArgumentException("Unknown local marketplace bundle: " + bundleId);
        }

        try (Stream<Path> dirs = Files.list(root)) {
            for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
                Map<String, Object> row = loadBundleListing(dir);
                String slug = stringValue(row.get("slug"));
                String directory = stringValue(row.get("directory"));
                if (bundleId.equals(slug) || bundleId.equals(directory)) {
                    return new ResolvedListing(
                            row,
                            dir.resolve("listing.manifest.json"),
                            dir.resolve("bundle.json")
                    );
                }
            }
        }
        throw new IllegalArgumentException("Unknown local marketplace bundle: " + bundleId);
    }

    private Map<String, Object> loadBundleListing(Path dir) throws Exception {
        Path listingPath = dir.resolve("listing.manifest.json");
        Path bundlePath = dir.resolve("bundle.json");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("directory", dir.getFileName().toString());

        if (!Files.isRegularFile(listingPath)) {
            row.put("validationStatus", "MISSING_LISTING");
            row.put("errors", List.of("listing.manifest.json not found"));
            return row;
        }
        if (!Files.isRegularFile(bundlePath)) {
            row.put("validationStatus", "MISSING_BUNDLE");
            row.put("errors", List.of("bundle.json not found"));
            return row;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> listing = objectMapper.readValue(Files.readString(listingPath), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> bundle = objectMapper.readValue(Files.readString(bundlePath), Map.class);

        List<String> errors = validateListing(listing, bundle);
        row.put("slug", listing.get("slug"));
        row.put("title", listing.get("title"));
        row.put("description", listing.get("description"));
        row.put("appId", listing.get("appId"));
        row.put("pricing", listing.getOrDefault("pricing", "free"));
        row.put("latestVersion", listing.get("latestVersion"));
        row.put("minIspfVersion", listing.get("minIspfVersion"));
        row.put("vendorName", listing.get("vendorName"));
        row.put("bundleVersion", bundle.get("version"));
        row.put("displayName", bundle.get("displayName"));
        row.put("listingPath", listingPath.toString());
        row.put("bundlePath", bundlePath.toString());
        row.put("validationStatus", errors.isEmpty() ? "OK" : "INVALID");
        if (!errors.isEmpty()) {
            row.put("errors", errors);
        }
        return row;
    }

    private List<String> validateListing(Map<String, Object> listing, Map<String, Object> bundle) {
        List<String> errors = new ArrayList<>();
        for (String key : LISTING_REQUIRED) {
            Object value = listing.get(key);
            if (value == null || String.valueOf(value).isBlank()) {
                errors.add("listing missing " + key);
            }
        }
        for (String key : BUNDLE_REQUIRED) {
            Object value = bundle.get(key);
            if (value == null || String.valueOf(value).isBlank()) {
                errors.add("bundle missing " + key);
            }
        }
        String listingAppId = stringValue(listing.get("appId"));
        String bundleAppId = operatorUiAppId(bundle);
        if (!listingAppId.isBlank() && !bundleAppId.isBlank() && !listingAppId.equals(bundleAppId)) {
            errors.add("listing.appId mismatch with bundle operatorUi.appId");
        }
        String listingVersion = stringValue(listing.get("latestVersion"));
        String bundleVersion = stringValue(bundle.get("version"));
        if (!listingVersion.isBlank() && !bundleVersion.isBlank() && !listingVersion.equals(bundleVersion)) {
            errors.add("latestVersion mismatch with bundle.version");
        }
        return errors;
    }

    @SuppressWarnings("unchecked")
    private static String operatorUiAppId(Map<String, Object> bundle) {
        Object operatorUi = bundle.get("operatorUi");
        if (operatorUi instanceof Map<?, ?> map && map.get("appId") != null) {
            return String.valueOf(map.get("appId")).trim();
        }
        return "";
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Path resolveLocalRoot() {
        String configured = properties.getLocalBundlesDir();
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured.trim());
        }
        String env = System.getenv("ISPF_MARKETPLACE_LOCAL_DIR");
        if (env != null && !env.isBlank()) {
            return Paths.get(env.trim());
        }
        Path cwd = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (int depth = 0; depth <= 4; depth++) {
            Path catalog = cwd.resolve("examples/marketplace-catalog");
            if (Files.isDirectory(catalog)) {
                return catalog;
            }
            Path demo = cwd.resolve("examples/marketplace-demo");
            if (Files.isDirectory(demo)) {
                return demo;
            }
            Path parent = cwd.getParent();
            if (parent == null) {
                break;
            }
            cwd = parent;
        }
        Path catalog = Paths.get("examples/marketplace-catalog");
        if (Files.isDirectory(catalog)) {
            return catalog;
        }
        return Paths.get("examples/marketplace-demo");
    }
}
