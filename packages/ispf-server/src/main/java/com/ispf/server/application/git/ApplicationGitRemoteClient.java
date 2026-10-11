package com.ispf.server.application.git;

import com.ispf.server.security.OutboundUrlSafety;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches {@code bundle.json} (or configured path) from GitHub / GitLab Contents APIs (ADR-0063).
 */
@Component
public class ApplicationGitRemoteClient {

    private static final int MAX_BUNDLE_BYTES = 8 * 1024 * 1024;
    private static final Pattern GITHUB_PATH = Pattern.compile("^/([^/]+)/([^/]+?)(?:\\.git)?/?$");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ApplicationGitRemoteClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public FetchedBundle fetchBundle(
            String provider,
            String remoteUrl,
            String bundlePath,
            String ref,
            String token
    ) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "git credential is missing");
        }
        String normalizedProvider = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        return switch (normalizedProvider) {
            case "github" -> fetchGitHub(remoteUrl, bundlePath, ref, token.trim());
            case "gitlab" -> fetchGitLab(remoteUrl, bundlePath, ref, token.trim());
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "unsupported git provider: " + provider
            );
        };
    }

    private FetchedBundle fetchGitHub(String remoteUrl, String bundlePath, String ref, String token) {
        RepoCoords coords = parseGitHubCoords(remoteUrl);
        URI apiBase = githubApiBase(coords.host());
        // Resolve commit first, then fetch contents at that exact sha (avoids tip race).
        String commitSha = fetchGitHubCommitSha(apiBase, coords, ref, token);
        String encodedPath = encodePathSegments(bundlePath);
        URI contentsUri = OutboundUrlSafety.resolveEncodedPath(
                apiBase,
                "repos/" + coords.owner() + "/" + coords.repo() + "/contents/" + encodedPath + "?ref="
                        + URLEncoder.encode(commitSha, StandardCharsets.UTF_8)
        );
        JsonNode body = sendJson(contentsUri, token, "Bearer", "application/vnd.github+json");
        String encoding = text(body, "encoding");
        String content = text(body, "content");
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub contents response missing content");
        }
        if (encoding != null && !"base64".equalsIgnoreCase(encoding)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "unsupported GitHub contents encoding: " + encoding
            );
        }
        String json = decodeBase64Content(content);
        return new FetchedBundle(json, commitSha, ref);
    }

    private String fetchGitHubCommitSha(URI apiBase, RepoCoords coords, String ref, String token) {
        URI commitUri = OutboundUrlSafety.resolveEncodedPath(
                apiBase,
                "repos/" + coords.owner() + "/" + coords.repo() + "/commits/"
                        + URLEncoder.encode(ref, StandardCharsets.UTF_8)
        );
        JsonNode body = sendJson(commitUri, token, "Bearer", "application/vnd.github+json");
        String sha = text(body, "sha");
        if (sha == null || sha.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub commit sha missing");
        }
        return sha;
    }

    private FetchedBundle fetchGitLab(String remoteUrl, String bundlePath, String ref, String token) {
        GitLabCoords coords = parseGitLabCoords(remoteUrl);
        URI apiBase = OutboundUrlSafety.requireSafePublicHttpsUrl(
                "https://" + coords.host() + "/api/v4"
        );
        String project = URLEncoder.encode(coords.projectPath(), StandardCharsets.UTF_8);
        String filePath = URLEncoder.encode(bundlePath, StandardCharsets.UTF_8);
        URI fileUri = OutboundUrlSafety.resolveEncodedPath(
                apiBase,
                "projects/" + project + "/repository/files/" + filePath + "?ref="
                        + URLEncoder.encode(ref, StandardCharsets.UTF_8)
        );
        JsonNode body = sendJson(fileUri, token, "PRIVATE-TOKEN", null);
        String encoding = text(body, "encoding");
        String content = text(body, "content");
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitLab file response missing content");
        }
        if (encoding != null && !"base64".equalsIgnoreCase(encoding)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "unsupported GitLab file encoding: " + encoding
            );
        }
        String json = decodeBase64Content(content);
        String commitSha = text(body, "commit_id");
        if (commitSha == null || commitSha.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitLab commit_id missing");
        }
        return new FetchedBundle(json, commitSha, ref);
    }

    private JsonNode sendJson(URI uri, String token, String authMode, String accept) {
        OutboundUrlSafety.requireSafePublicHttpsUrl(uri.toString());
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "ispf-server")
                    .GET();
            if (accept != null) {
                builder.header("Accept", accept);
            } else {
                builder.header("Accept", "application/json");
            }
            if ("PRIVATE-TOKEN".equals(authMode)) {
                builder.header("PRIVATE-TOKEN", token);
            } else {
                builder.header("Authorization", "Bearer " + token);
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 401 || status == 403) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "git remote auth failed (HTTP " + status + ")");
            }
            if (status == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "bundle file not found on remote");
            }
            if (status >= 400) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "git remote returned HTTP " + status
                );
            }
            String body = response.body() == null ? "" : response.body();
            if (body.length() > MAX_BUNDLE_BYTES * 2) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "remote response too large");
            }
            return objectMapper.readTree(body);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "git remote request failed: " + ex.getMessage(),
                    ex
            );
        }
    }

    private static String decodeBase64Content(String content) {
        String compact = content.replaceAll("\\s", "");
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(compact);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "invalid base64 content from git remote", ex);
        }
        if (decoded.length > MAX_BUNDLE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bundle exceeds size limit");
        }
        return new String(decoded, StandardCharsets.UTF_8);
    }

    static RepoCoords parseGitHubCoords(String remoteUrl) {
        URI uri = OutboundUrlSafety.requireSafePublicHttpsUrl(remoteUrl);
        Matcher matcher = GITHUB_PATH.matcher(uri.getPath() == null ? "" : uri.getPath());
        if (!matcher.matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "remoteUrl must be https://host/owner/repo(.git)"
            );
        }
        String owner = matcher.group(1);
        String repo = matcher.group(2);
        if (owner.isBlank() || repo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "remoteUrl owner/repo required");
        }
        if (!isSafeGitHubSegment(owner) || !isSafeGitHubSegment(repo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "remoteUrl owner/repo is invalid");
        }
        return new RepoCoords(uri.getHost().toLowerCase(Locale.ROOT), owner, repo);
    }

    static GitLabCoords parseGitLabCoords(String remoteUrl) {
        URI uri = OutboundUrlSafety.requireSafePublicHttpsUrl(remoteUrl);
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (path.endsWith(".git")) {
            path = path.substring(0, path.length() - 4);
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isBlank() || !path.contains("/")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "remoteUrl must be https://host/group/project(.git)"
            );
        }
        return new GitLabCoords(uri.getHost().toLowerCase(Locale.ROOT), path);
    }

    private static URI githubApiBase(String host) {
        if ("github.com".equalsIgnoreCase(host)) {
            return OutboundUrlSafety.requireSafePublicHttpsUrl("https://api.github.com");
        }
        return OutboundUrlSafety.requireSafePublicHttpsUrl("https://" + host + "/api/v3");
    }

    private static boolean isSafeGitHubSegment(String value) {
        return value != null
                && !".".equals(value)
                && !"..".equals(value)
                && value.matches("[A-Za-z0-9_.-]+");
    }

    private static String encodePathSegments(String bundlePath) {
        String[] parts = bundlePath.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append('/');
            }
            sb.append(URLEncoder.encode(parts[i], StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return sb.toString();
    }

    private static String text(JsonNode node, String field) {
        if (node == null || field == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    public record FetchedBundle(String contentJson, String commitSha, String ref) {
    }

    record RepoCoords(String host, String owner, String repo) {
    }

    record GitLabCoords(String host, String projectPath) {
    }
}
