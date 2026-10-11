package com.ispf.server.application.git;

import com.ispf.server.application.bundle.ApplicationBundleSnapshotStore;
import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.security.PlatformCredentialService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * APPLICATION ↔ team git binding (ADR-0063). Pull/push providers land in follow-up PRs;
 * this service owns Connect / Status.
 */
@Service
public class ApplicationGitService {

    private static final Set<String> PROVIDERS = Set.of("github", "gitlab");

    private final ApplicationGitBindingStore bindingStore;
    private final ApplicationDataStore dataStore;
    private final ApplicationBundleSnapshotStore snapshotStore;
    private final PlatformCredentialService credentialService;

    public ApplicationGitService(
            ApplicationGitBindingStore bindingStore,
            ApplicationDataStore dataStore,
            ApplicationBundleSnapshotStore snapshotStore,
            PlatformCredentialService credentialService
    ) {
        this.bindingStore = bindingStore;
        this.dataStore = dataStore;
        this.snapshotStore = snapshotStore;
        this.credentialService = credentialService;
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
                "Connect/Status available now. Pull release and Push PR are planned follow-ups (ADR-0063)."
        );
        return status;
    }

    static String defaultCredentialPath(String appId) {
        return "root.platform.applications." + appId + ".git-credential";
    }

    static String validateRemoteUrl(String raw) {
        String value = requireText(raw, "remoteUrl");
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "remoteUrl is not a valid URI", ex);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "remoteUrl must use https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "remoteUrl host is required");
        }
        if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "remoteUrl must not embed credentials; use the token field"
            );
        }
        if (uri.getQuery() != null || uri.getFragment() != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "remoteUrl must not include query or fragment"
            );
        }
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
}
