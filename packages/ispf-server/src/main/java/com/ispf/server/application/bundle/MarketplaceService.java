package com.ispf.server.application.bundle;

import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.platform.analytics.pack.DropInAnalyticsPackLoader;
import com.ispf.server.config.MarketplaceProperties;
import com.ispf.server.license.CommercialBundleLicenseSigner;
import com.ispf.server.license.InstallationIdService;
import com.ispf.server.platform.update.PlatformVersionSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class MarketplaceService {

    private final MarketplaceProperties properties;
    private final ApplicationDataStore dataStore;
    private final ApplicationBundleSnapshotStore snapshotStore;
    private final ApplicationBundleDeployService deployService;
    private final InstallationIdService installationIdService;
    private final CommercialBundleLicenseSigner bundleLicenseSigner;
    private final Optional<BuildProperties> buildProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final DropInAnalyticsPackLoader analyticsPackLoader;

    @Autowired
    public MarketplaceService(
            MarketplaceProperties properties,
            ApplicationDataStore dataStore,
            ApplicationBundleSnapshotStore snapshotStore,
            ApplicationBundleDeployService deployService,
            InstallationIdService installationIdService,
            CommercialBundleLicenseSigner bundleLicenseSigner,
            Optional<BuildProperties> buildProperties,
            ObjectMapper objectMapper,
            DropInAnalyticsPackLoader analyticsPackLoader
    ) {
        this(
                properties,
                dataStore,
                snapshotStore,
                deployService,
                installationIdService,
                bundleLicenseSigner,
                buildProperties,
                objectMapper,
                analyticsPackLoader,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build()
        );
    }

    MarketplaceService(
            MarketplaceProperties properties,
            ApplicationDataStore dataStore,
            ApplicationBundleSnapshotStore snapshotStore,
            ApplicationBundleDeployService deployService,
            InstallationIdService installationIdService,
            CommercialBundleLicenseSigner bundleLicenseSigner,
            Optional<BuildProperties> buildProperties,
            ObjectMapper objectMapper,
            DropInAnalyticsPackLoader analyticsPackLoader,
            HttpClient httpClient
    ) {
        this.properties = properties;
        this.dataStore = dataStore;
        this.snapshotStore = snapshotStore;
        this.deployService = deployService;
        this.installationIdService = installationIdService;
        this.bundleLicenseSigner = bundleLicenseSigner;
        this.buildProperties = buildProperties;
        this.objectMapper = objectMapper;
        this.analyticsPackLoader = analyticsPackLoader;
        this.httpClient = httpClient;
    }

    public Map<String, Object> listMarketplaces() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("enabled", properties.isEnabled());
        response.put("defaultId", properties.getDefaultId());
        List<Map<String, Object>> endpoints = new ArrayList<>();
        for (MarketplaceProperties.Endpoint endpoint : properties.getEndpoints()) {
            if (endpoint.getBaseUrl() == null || endpoint.getBaseUrl().isBlank()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", endpoint.getId());
            row.put("name", endpoint.getName());
            row.put("baseUrl", normalizeBaseUrl(endpoint.getBaseUrl()));
            row.put("contactUrl", endpoint.getContactUrl());
            row.put("default", endpoint.isDefaultEndpoint()
                    || endpoint.getId().equals(properties.getDefaultId()));
            endpoints.add(row);
        }
        response.put("endpoints", endpoints);
        return response;
    }

    public Map<String, Object> browseCatalog(String marketplaceId, String query, String pricingFilter, String kindFilter)
            throws Exception {
        MarketplaceProperties.Endpoint endpoint = requireEndpoint(marketplaceId);
        @SuppressWarnings("unchecked")
        Map<String, Object> remote = fetchJson(
                normalizeBaseUrl(endpoint.getBaseUrl()) + "/api/v1/catalog",
                Map.class
        );
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> listings = remote.get("listings") instanceof List<?> raw
                ? (List<Map<String, Object>>) raw
                : List.of();

        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Map<String, Object> listing : listings) {
            Map<String, Object> row = new LinkedHashMap<>(listing);
            String artifactKind = stringValue(listing.get("artifactKind"));
            if ("analytics-pack".equalsIgnoreCase(artifactKind)) {
                String packId = stringValue(listing.get("packId"));
                if (packId != null) {
                    row.put("installed", analyticsPackLoader.isPackInstalled(packId));
                }
            } else {
                String appId = stringValue(listing.get("appId"));
                if (appId != null) {
                    row.put("installed", dataStore.findApp(appId).isPresent());
                    String latestVersion = stringValue(listing.get("latestVersion"));
                    snapshotStore.findActive(appId).ifPresent(active -> {
                        row.put("activeVersion", active.bundleVersion());
                        if (latestVersion != null && !latestVersion.isBlank()) {
                            row.put(
                                    "updateAvailable",
                                    PlatformVersionSupport.isUpdateAvailable(active.bundleVersion(), latestVersion)
                            );
                        }
                    });
                }
            }
            if (endpoint.getContactUrl() != null && !endpoint.getContactUrl().isBlank()) {
                row.putIfAbsent("marketplaceContactUrl", endpoint.getContactUrl());
            }
            enriched.add(row);
        }

        List<Map<String, Object>> filtered = filterListings(enriched, query, pricingFilter, kindFilter);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("marketplaceId", endpoint.getId());
        response.put("marketplaceName", endpoint.getName());
        response.put("contactUrl", endpoint.getContactUrl());
        response.put("listings", filtered);
        response.put("total", filtered.size());
        return response;
    }

    public Map<String, Object> installFreeListing(String marketplaceId, String slug) throws Exception {
        MarketplaceProperties.Endpoint endpoint = requireEndpoint(marketplaceId);
        Map<String, Object> detail = fetchListingDetail(endpoint, slug);
        if (!"free".equalsIgnoreCase(stringValue(detail.get("pricing")))) {
            throw new IllegalArgumentException("Listing is not free — use activation with entitlement key");
        }
        assertListingCompatible(detail);
        if (isAnalyticsPackListing(detail)) {
            return installAnalyticsPackListing(endpoint, slug, detail, "free-download");
        }
        String appId = requireAppId(detail);
        String installationId = installationIdService.ensureInstallationId();
        String manifestJson = httpGet(buildFreeDownloadUrl(endpoint, slug, installationId));
        return deployManifest(
                appId,
                manifestJson,
                marketplaceId,
                slug,
                "free-download",
                stringValue(detail.get("latestVersion")),
                resolveTrustedFreeInstall(manifestJson)
        );
    }

    public Map<String, Object> activatePaidListing(
            String marketplaceId,
            String slug,
            String activationCode
    ) throws Exception {
        if (activationCode == null || activationCode.isBlank()) {
            throw new IllegalArgumentException("activationCode is required");
        }
        MarketplaceProperties.Endpoint endpoint = requireEndpoint(marketplaceId);
        Map<String, Object> detail = fetchListingDetail(endpoint, slug);
        if (!"paid".equalsIgnoreCase(stringValue(detail.get("pricing")))) {
            throw new IllegalArgumentException("Listing is not paid — use free install");
        }
        assertListingCompatible(detail);
        if (isAnalyticsPackListing(detail)) {
            String installationId = installationIdService.ensureInstallationId();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("activationCode", activationCode.trim());
            body.put("installationId", installationId);
            body.put("slug", slug);

            @SuppressWarnings("unchecked")
            Map<String, Object> activated = postJson(
                    normalizeBaseUrl(endpoint.getBaseUrl()) + "/api/v1/entitlements/activate",
                    body,
                    Map.class
            );
            Object artifact = activated.get("artifactBytesBase64");
            if (artifact == null) {
                artifact = activated.get("bundle");
            }
            if (artifact == null) {
                throw new IllegalStateException("Marketplace did not return analytics pack artifact");
            }
            byte[] zipBytes = decodeArtifactBytes(artifact);
            Map<String, Object> result = installAnalyticsPackZip(
                    detail,
                    zipBytes,
                    marketplaceId,
                    slug,
                    "paid-activation"
            );
            result.put("installationId", installationId);
            return result;
        }
        String appId = requireAppId(detail);
        String installationId = installationIdService.ensureInstallationId();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("activationCode", activationCode.trim());
        body.put("installationId", installationId);
        body.put("slug", slug);

        @SuppressWarnings("unchecked")
        Map<String, Object> activated = postJson(
                normalizeBaseUrl(endpoint.getBaseUrl()) + "/api/v1/entitlements/activate",
                body,
                Map.class
        );

        Object bundle = activated.get("bundle");
        if (bundle == null) {
            throw new IllegalStateException("Marketplace did not return signed bundle");
        }
        String manifestJson = objectMapper.writeValueAsString(bundle);
        Map<String, Object> result = deployManifest(
                appId,
                manifestJson,
                marketplaceId,
                slug,
                "paid-activation",
                stringValue(detail.get("latestVersion")),
                false
        );
        result.put("installationId", installationId);
        return result;
    }

    private Map<String, Object> deployManifest(
            String appId,
            String manifestJson,
            String marketplaceId,
            String slug,
            String source,
            String listingVersion,
            boolean trustedMarketplaceFreeInstall
    ) throws Exception {
        String previousVersion = snapshotStore.findActive(appId)
                .map(ApplicationBundleSnapshotStore.BundleSnapshot::bundleVersion)
                .orElse(null);
        ApplicationBundleDeployService.BundleManifest manifest =
                BundleManifestJsonSupport.parse(objectMapper, manifestJson);
        if (trustedMarketplaceFreeInstall && bundleLicenseSigner.isConfigured()) {
            Map<String, Object> signed = bundleLicenseSigner.signManifestIfNeeded(appId, manifest);
            manifest = BundleManifestJsonSupport.parse(objectMapper, objectMapper.writeValueAsString(signed));
            trustedMarketplaceFreeInstall = false;
        }
        Map<String, Object> deployResult = deployService.deploy(appId, manifest, trustedMarketplaceFreeInstall);
        Map<String, Object> result = new LinkedHashMap<>(deployResult);
        result.put("marketplaceId", marketplaceId);
        result.put("listingSlug", slug);
        result.put("installedFrom", source);
        result.put("installedVersion", manifest.version());
        result.put("listingVersion", listingVersion);
        if (previousVersion != null) {
            result.put("previousVersion", previousVersion);
            result.put("upgrade", PlatformVersionSupport.isUpdateAvailable(previousVersion, manifest.version()));
        }
        return result;
    }

    private boolean resolveTrustedFreeInstall(String manifestJson) throws Exception {
        ApplicationBundleDeployService.BundleManifest manifest =
                BundleManifestJsonSupport.parse(objectMapper, manifestJson);
        @SuppressWarnings("unchecked")
        Map<String, Object> root = objectMapper.convertValue(manifest, Map.class);
        return root.get("license") == null && !bundleLicenseSigner.isConfigured();
    }

    private void assertListingCompatible(Map<String, Object> detail) {
        String minIspfVersion = stringValue(detail.get("minIspfVersion"));
        if (minIspfVersion == null || minIspfVersion.isBlank()) {
            return;
        }
        String platformVersion = PlatformVersionSupport.currentVersion(buildProperties);
        if (PlatformVersionSupport.compare(platformVersion, minIspfVersion) < 0) {
            throw new IllegalArgumentException(
                    "Listing requires ISPF " + minIspfVersion + " or newer (current " + platformVersion + ")"
            );
        }
    }

    private static String buildFreeDownloadUrl(
            MarketplaceProperties.Endpoint endpoint,
            String slug,
            String installationId
    ) {
        String base = normalizeBaseUrl(endpoint.getBaseUrl())
                + "/api/v1/catalog/" + encodeSlug(slug) + "/download";
        if (installationId == null || installationId.isBlank()) {
            return base;
        }
        return base + "?installationId=" + URLEncoder.encode(installationId, StandardCharsets.UTF_8);
    }

    private Map<String, Object> installAnalyticsPackListing(
            MarketplaceProperties.Endpoint endpoint,
            String slug,
            Map<String, Object> detail,
            String source
    ) throws Exception {
        String installationId = installationIdService.ensureInstallationId();
        byte[] zipBytes = httpGetBytes(buildFreeDownloadUrl(endpoint, slug, installationId));
        return installAnalyticsPackZip(detail, zipBytes, endpoint.getId(), slug, source);
    }

    private Map<String, Object> installAnalyticsPackZip(
            Map<String, Object> detail,
            byte[] zipBytes,
            String marketplaceId,
            String slug,
            String source
    ) throws Exception {
        String packId = requirePackId(detail);
        List<String> helpers = analyticsPackLoader.installZipArchive(zipBytes, packId);
        if (helpers == null || helpers.isEmpty()) {
            throw new IllegalStateException(
                    "Analytics pack install failed — license verification or helper registration"
            );
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "OK");
        result.put("artifactKind", "analytics-pack");
        result.put("packId", packId);
        result.put("functions", helpers);
        result.put("marketplaceId", marketplaceId);
        result.put("listingSlug", slug);
        result.put("installedFrom", source);
        return result;
    }

    private static boolean isAnalyticsPackListing(Map<String, Object> detail) {
        String artifactKind = stringValue(detail.get("artifactKind"));
        if ("analytics-pack".equalsIgnoreCase(artifactKind)) {
            return true;
        }
        String kind = stringValue(detail.get("kind"));
        return "analytics-pack".equalsIgnoreCase(kind) || "analytics_pack".equalsIgnoreCase(kind);
    }

    private static String requirePackId(Map<String, Object> detail) {
        String packId = stringValue(detail.get("packId"));
        if (packId == null || packId.isBlank()) {
            packId = stringValue(detail.get("slug"));
        }
        if (packId == null || packId.isBlank()) {
            throw new IllegalStateException("Listing has no packId");
        }
        return packId;
    }

    private byte[] decodeArtifactBytes(Object artifact) throws Exception {
        if (artifact instanceof String encoded && !encoded.isBlank()) {
            return java.util.Base64.getDecoder().decode(encoded.trim());
        }
        if (artifact instanceof byte[] bytes) {
            return bytes;
        }
        if (artifact instanceof Map<?, ?> map && map.get("zipBytesBase64") instanceof String encoded) {
            return java.util.Base64.getDecoder().decode(encoded.trim());
        }
        String json = objectMapper.writeValueAsString(artifact);
        return json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private byte[] httpGetBytes(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Accept", "application/octet-stream, application/zip, application/json")
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() >= 400) {
            throw new MarketplaceRemoteException(response.statusCode(), new String(response.body()));
        }
        return response.body();
    }

    private Map<String, Object> fetchListingDetail(MarketplaceProperties.Endpoint endpoint, String slug)
            throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> detail = fetchJson(
                normalizeBaseUrl(endpoint.getBaseUrl()) + "/api/v1/catalog/" + encodeSlug(slug),
                Map.class
        );
        return detail;
    }

    private String requireAppId(Map<String, Object> detail) {
        String appId = stringValue(detail.get("appId"));
        if (appId == null || appId.isBlank()) {
            throw new IllegalStateException("Listing has no appId");
        }
        return appId;
    }

    private MarketplaceProperties.Endpoint requireEndpoint(String marketplaceId) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Marketplace integration is disabled");
        }
        String normalized = marketplaceId == null ? "" : marketplaceId.trim();
        Optional<MarketplaceProperties.Endpoint> match = properties.getEndpoints().stream()
                .filter(e -> e.getId().equals(normalized))
                .findFirst();
        if (match.isEmpty() || match.get().getBaseUrl() == null || match.get().getBaseUrl().isBlank()) {
            throw new IllegalArgumentException("Unknown marketplace: " + marketplaceId);
        }
        return match.get();
    }

    private List<Map<String, Object>> filterListings(
            List<Map<String, Object>> listings,
            String query,
            String pricingFilter,
            String kindFilter
    ) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String pricing = pricingFilter == null ? "all" : pricingFilter.trim().toLowerCase(Locale.ROOT);
        String kind = kindFilter == null ? "all" : kindFilter.trim().toLowerCase(Locale.ROOT);
        return listings.stream()
                .filter(row -> matchesPricing(row, pricing))
                .filter(row -> matchesKind(row, kind))
                .filter(row -> matchesQuery(row, q))
                .toList();
    }

    private static boolean matchesPricing(Map<String, Object> row, String pricing) {
        if ("all".equals(pricing) || pricing.isBlank()) {
            return true;
        }
        String rowPricing = stringValue(row.get("pricing"));
        return rowPricing != null && pricing.equalsIgnoreCase(rowPricing);
    }

    private static boolean matchesKind(Map<String, Object> row, String kind) {
        if ("all".equals(kind) || kind.isBlank()) {
            return true;
        }
        String raw = normalizeListingKind(
                stringValue(row.get("artifactKind")),
                stringValue(row.get("kind"))
        );
        return kind.equalsIgnoreCase(raw);
    }

    /** Normalize artifactKind/kind to one of the marketplace filter categories. */
    private static String normalizeListingKind(String artifactKind, String kind) {
        String raw = artifactKind != null && !artifactKind.isBlank() ? artifactKind : kind;
        if (raw == null || raw.isBlank()) {
            return "application";
        }
        raw = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if ("application-bundle".equals(raw) || "application".equals(raw)) {
            return "application";
        }
        if ("analytics-pack".equals(raw)) {
            return "analytics-pack";
        }
        if ("symbol-pack".equals(raw)) {
            return "symbol-pack";
        }
        if (raw.contains("driver") || raw.equals("driver-pack")) {
            return "driver";
        }
        if (raw.contains("workflow") || raw.equals("workflow-template")) {
            return "workflow-template";
        }
        if (raw.contains("report") || raw.equals("report-template")) {
            return "report-template";
        }
        if (raw.contains("ai") || raw.contains("llm") || raw.contains("ollama") || raw.contains("openai")
                || raw.equals("ai-provider")) {
            return "ai-provider";
        }
        if (raw.contains("binding") || raw.equals("binding-pack")) {
            return "binding-pack";
        }
        if (raw.contains("plugin")) {
            return "plugin";
        }
        return "other";
    }

    private static boolean matchesQuery(Map<String, Object> row, String q) {
        if (q.isBlank()) {
            return true;
        }
        return containsIgnoreCase(row.get("title"), q)
                || containsIgnoreCase(row.get("description"), q)
                || containsIgnoreCase(row.get("slug"), q)
                || containsIgnoreCase(row.get("appId"), q)
                || containsIgnoreCase(row.get("packId"), q)
                || containsIgnoreCase(row.get("vendorName"), q);
    }

    private static boolean containsIgnoreCase(Object value, String q) {
        return value != null && String.valueOf(value).toLowerCase(Locale.ROOT).contains(q);
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String encodeSlug(String slug) {
        return slug;
    }

    private <T> T fetchJson(String url, Class<T> type) throws Exception {
        String body = httpGet(url);
        return objectMapper.readValue(body, type);
    }

    private <T> T postJson(String url, Object payload, Class<T> type) throws Exception {
        String json = objectMapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new MarketplaceRemoteException(response.statusCode(), response.body());
        }
        return objectMapper.readValue(response.body(), type);
    }

    private String httpGet(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new MarketplaceRemoteException(response.statusCode(), response.body());
        }
        return response.body();
    }

    public static class MarketplaceRemoteException extends RuntimeException {
        private final int statusCode;

        public MarketplaceRemoteException(int statusCode, String body) {
            super("Marketplace HTTP " + statusCode + ": " + body);
            this.statusCode = statusCode;
        }

        public int statusCode() {
            return statusCode;
        }
    }
}
