package com.ispf.server.application.git;

import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import com.ispf.server.application.bundle.ApplicationBundleSnapshotStore;
import com.ispf.server.application.bundle.BundleManifestValidator;
import com.ispf.server.application.bundle.BundleValidationResult;
import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.license.CommercialBundleLicenseVerifier;
import com.ispf.server.license.CommercialLicenseException;
import com.ispf.server.security.OutboundUrlSafety;
import com.ispf.server.security.PlatformCredentialService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * APPLICATION ↔ team git binding (ADR-0063). Connect / Status / Pull release;
 * Push PR is a follow-up.
 */
@Service
public class ApplicationGitService {

    private static final Set<String> PROVIDERS = Set.of("github", "gitlab");
    private static final Pattern SAFE_REF = Pattern.compile("^[A-Za-z0-9._/-]{1,255}$");

    private final ApplicationGitBindingStore bindingStore;
    private final ApplicationDataStore dataStore;
    private final ApplicationBundleSnapshotStore snapshotStore;
    private final PlatformCredentialService credentialService;
    private final ApplicationGitRemoteClient remoteClient;
    private final ApplicationBundleDeployService bundleDeployService;
    private final BundleManifestValidator bundleManifestValidator;
    private final CommercialBundleLicenseVerifier licenseVerifier;
    private final ObjectMapper objectMapper;

    public ApplicationGitService(
            ApplicationGitBindingStore bindingStore,
            ApplicationDataStore dataStore,
            ApplicationBundleSnapshotStore snapshotStore,
            PlatformCredentialService credentialService,
            ApplicationGitRemoteClient remoteClient,
            ApplicationBundleDeployService bundleDeployService,
            BundleManifestValidator bundleManifestValidator,
            CommercialBundleLicenseVerifier licenseVerifier,
            ObjectMapper objectMapper
    ) {
        this.bindingStore = bindingStore;
        this.dataStore = dataStore;
        this.snapshotStore = snapshotStore;
        this.credentialService = credentialService;
        this.remoteClient = remoteClient;
        this.bundleDeployService = bundleDeployService;
        this.bundleManifestValidator = bundleManifestValidator;
        this.licenseVerifier = licenseVerifier;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getBinding(String appId) {
        requireApp(appId);
        return bindingStore.find(appId)
                .map(this::publicBinding)
                .orElseGet(() -> Map.of("appId", appId, "configured", false));
    }

    @Transactional
    public Map<String, Object> upsertBinding(String appId, GitBindingRequest request) {
        requireApp(appId);
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body is required");
        }
        String provider = normalizeProvider(request.provider());
        String remoteUrl = validateRemoteUrl(request.remoteUrl());
        String defaultBranch = blankTo(request.defaultBranch(), "main");
        String bundlePath = validateBundlePath(blankTo(request.bundlePath(), "bundle.json"));
        String credentialPath = defaultCredentialPath(appId);
        var existing = bindingStore.find(appId);
        boolean hasToken = request.token() != null && !request.token().isBlank();

        if (hasToken) {
            credentialService.upsert(
                    credentialPath,
                    "git-" + provider,
                    request.token().trim(),
                    Map.of("appId", appId, "provider", provider, "remoteUrl", remoteUrl)
            );
        } else if (existing.isEmpty() || !credentialConfigured(credentialPath)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "token is required on first connect"
            );
        } else if (remoteOrProviderChanged(existing.get(), provider, remoteUrl)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "token is required when provider or remote host changes"
            );
        }

        bindingStore.upsert(appId, provider, remoteUrl, defaultBranch, bundlePath, credentialPath);
        return publicBinding(bindingStore.find(appId).orElseThrow());
    }

    @Transactional
    public Map<String, Object> disconnect(String appId) {
        requireApp(appId);
        String credentialPath = defaultCredentialPath(appId);
        bindingStore.clear(appId);
        credentialService.deleteByObjectPath(credentialPath);
        return Map.of("appId", appId, "configured", false);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> status(String appId) {
        requireApp(appId);
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("appId", appId);
        var binding = bindingStore.find(appId);
        status.put("configured", binding.isPresent());
        binding.ifPresent(row -> status.put("binding", publicBinding(row)));

        var active = snapshotStore.findActive(appId);
        status.put("hasDeploy", active.isPresent());
        active.ifPresent(snapshot -> {
            status.put("deployedVersion", snapshot.bundleVersion());
            status.put("deployedAt", snapshot.deployedAt() != null ? snapshot.deployedAt().toString() : null);
        });

        boolean credentialOk = binding
                .map(row -> row.get("credentialObjectPath"))
                .map(String::valueOf)
                .filter(path -> path != null && !path.isBlank())
                .map(this::credentialConfigured)
                .orElse(false);
        status.put("credentialConfigured", credentialOk);
        status.put("remoteReachable", null);
        status.put("drift", Map.of(
                "treeVsDeploy", "unknown",
                "deployVsRemote", "unknown"
        ));
        status.put(
                "note",
                "Connect/Status/Pull available. Push PR is a planned follow-up (ADR-0063)."
        );
        return status;
    }

    /**
     * Fetch full bundle from the bound remote at {@code ref} (or default branch), validate, deploy.
     */
    public Map<String, Object> pullRelease(String appId, PullRequest request) {
        requireApp(appId);
        Map<String, Object> binding = bindingStore.find(appId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Git is not connected for application: " + appId
                ));
        String provider = String.valueOf(binding.get("provider"));
        String remoteUrl = String.valueOf(binding.get("remoteUrl"));
        String bundlePath = String.valueOf(binding.get("bundlePath"));
        String defaultBranch = String.valueOf(binding.getOrDefault("defaultBranch", "main"));
        String credentialPath = String.valueOf(binding.get("credentialObjectPath"));
        String ref = validateRef(
                request != null && request.ref() != null && !request.ref().isBlank()
                        ? request.ref()
                        : defaultBranch
        );

        String token = credentialService.resolveSecret(credentialPath)
                .filter(value -> value != null && !value.isBlank())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "git credential is missing; reconnect with a token"
                ));

        ApplicationGitRemoteClient.FetchedBundle fetched =
                remoteClient.fetchBundle(provider, remoteUrl, bundlePath, ref, token);

        @SuppressWarnings("unchecked")
        Map<String, Object> raw;
        ApplicationBundleDeployService.BundleManifest manifest;
        try {
            raw = objectMapper.readValue(fetched.contentJson(), Map.class);
            manifest = objectMapper.convertValue(raw, ApplicationBundleDeployService.BundleManifest.class);
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "remote bundle is not valid JSON: " + ex.getMessage(),
                    ex
            );
        }

        // License must be verified on the raw JSON map (same as POST /deploy). The typed
        // BundleManifest path used by validate() can break contentSha256 for signed bundles.
        try {
            licenseVerifier.verifyOrWarn(appId, raw, false);
        } catch (CommercialLicenseException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage(), ex);
        }

        BundleValidationResult validation = bundleManifestValidator.validate(appId, manifest);
        if (BundleValidationResult.ERROR.equals(validation.status())) {
            List<String> errors = validation.errors() == null ? List.of() : validation.errors().stream()
                    .filter(err -> err == null || !err.startsWith("license:"))
                    .toList();
            if (!errors.isEmpty()) {
                Map<String, Object> failed = new LinkedHashMap<>();
                failed.put("appId", appId);
                failed.put("status", "ERROR");
                Map<String, Object> validationMap = validation.toMap();
                validationMap.put("errors", errors);
                failed.put("validation", validationMap);
                failed.put("git", gitMeta(provider, remoteUrl, bundlePath, fetched));
                return failed;
            }
        }

        Map<String, Object> deployResult = bundleDeployService.deploy(appId, manifest, false, false);
        Map<String, Object> response = new LinkedHashMap<>(deployResult);
        response.put("git", gitMeta(provider, remoteUrl, bundlePath, fetched));

        boolean activated = Boolean.TRUE.equals(deployResult.get("snapshotActive"));
        String version = String.valueOf(deployResult.getOrDefault("version", manifest.version()));
        snapshotStore.updateGitProvenance(appId, version, fetched.ref(), fetched.commitSha());
        if (activated) {
            bindingStore.updateLastSynced(appId, fetched.commitSha(), version, Instant.now());
        }
        return response;
    }

    static String defaultCredentialPath(String appId) {
        return "root.platform.applications." + appId + ".git-credential";
    }

    static String validateRemoteUrl(String raw) {
        String value = requireText(raw, "remoteUrl");
        URI parsed;
        try {
            parsed = URI.create(value);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "remoteUrl is not a valid URI", ex);
        }
        if (parsed.getUserInfo() != null && !parsed.getUserInfo().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "remoteUrl must not embed credentials; use the token field"
            );
        }
        if (parsed.getQuery() != null || parsed.getFragment() != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "remoteUrl must not include query or fragment"
            );
        }
        // SSRF: https + public host (rebuild drops userInfo, so check it above first).
        OutboundUrlSafety.requireSafePublicHttpsUrl(value);
        return value;
    }

    static String validateBundlePath(String path) {
        if (path.contains("..") || path.startsWith("/") || path.contains("\\")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "bundlePath must be a relative path without '..'"
            );
        }
        return path;
    }

    static String validateRef(String ref) {
        String value = requireText(ref, "ref");
        if (value.contains("..") || value.startsWith("/") || value.contains("\\") || value.contains(" ")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ref is invalid");
        }
        if (!SAFE_REF.matcher(value).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ref must be a branch, tag, or sha (A-Za-z0-9._/-)"
            );
        }
        return value;
    }

    private static Map<String, Object> gitMeta(
            String provider,
            String remoteUrl,
            String bundlePath,
            ApplicationGitRemoteClient.FetchedBundle fetched
    ) {
        Map<String, Object> git = new LinkedHashMap<>();
        git.put("provider", provider);
        git.put("remoteUrl", remoteUrl);
        git.put("bundlePath", bundlePath);
        git.put("ref", fetched.ref());
        git.put("sha", fetched.commitSha());
        return git;
    }

    private Map<String, Object> publicBinding(Map<String, Object> row) {
        Map<String, Object> pub = new LinkedHashMap<>(row);
        pub.put("configured", true);
        return pub;
    }

    private static boolean remoteOrProviderChanged(Map<String, Object> existing, String provider, String remoteUrl) {
        String prevProvider = String.valueOf(existing.getOrDefault("provider", ""));
        String prevUrl = String.valueOf(existing.getOrDefault("remoteUrl", ""));
        if (!prevProvider.equalsIgnoreCase(provider)) {
            return true;
        }
        return !hostOf(prevUrl).equalsIgnoreCase(hostOf(remoteUrl));
    }

    private static String hostOf(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getHost() == null ? "" : uri.getHost();
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    private void requireApp(String appId) {
        if (appId == null || appId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "appId is required");
        }
        if (dataStore.findApp(appId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found: " + appId);
        }
    }

    private static String normalizeProvider(String provider) {
        String normalized = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        if (!PROVIDERS.contains(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "provider must be one of: github, gitlab"
            );
        }
        return normalized;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return value.trim();
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private boolean credentialConfigured(String credentialPath) {
        Map<String, Object> described = credentialService.describe(credentialPath);
        return Boolean.TRUE.equals(described.get("hasSecret"));
    }

    public record GitBindingRequest(
            String provider,
            String remoteUrl,
            String defaultBranch,
            String bundlePath,
            String token
    ) {
    }

    public record PullRequest(String ref) {
    }
}
