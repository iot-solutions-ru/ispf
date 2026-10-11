package com.ispf.server.application.git;

import com.ispf.server.security.OutboundUrlSafety;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GitHub / GitLab Contents APIs for APPLICATION Git lifecycle (ADR-0063): fetch bundle,
 * push feature-branch commit, open PR/MR. Never force-updates the default branch.
 */
@Component
public class ApplicationGitRemoteClient {

    private static final int MAX_BUNDLE_BYTES = 8 * 1024 * 1024;
    private static final Pattern GITHUB_PATH = Pattern.compile("^/([^/]+)/([^/]+?)(?:\\.git)?/?$");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public ApplicationGitRemoteClient(ObjectMapper objectMapper) {
        this(objectMapper, defaultHttpClient());
    }

    /** Package-private test seam — Spring uses the {@link Autowired} single-arg constructor only. */
    ApplicationGitRemoteClient(ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    private static HttpClient defaultHttpClient() {
        return HttpClient.newBuilder()
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
        requireToken(token);
        String normalizedProvider = normalizeProvider(provider);
        return switch (normalizedProvider) {
            case "github" -> fetchGitHub(remoteUrl, bundlePath, ref, token.trim());
            case "gitlab" -> fetchGitLab(remoteUrl, bundlePath, ref, token.trim());
            default -> throw unsupportedProvider(provider);
        };
    }

    public PushPrResult pushBundleAndOpenPr(
            String provider,
            String remoteUrl,
            String bundlePath,
            String defaultBranch,
            String featureBranch,
            String baseSha,
            String contentJson,
            String commitMessage,
            String prTitle,
            String prBody,
            String token
    ) {
        requireToken(token);
        if (contentJson == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bundle content is required");
        }
        byte[] utf8 = contentJson.getBytes(StandardCharsets.UTF_8);
        if (utf8.length > MAX_BUNDLE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bundle exceeds size limit");
        }
        if (defaultBranch != null && featureBranch != null
                && defaultBranch.trim().equalsIgnoreCase(featureBranch.trim())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "feature branch must not equal default branch"
            );
        }
        String normalizedProvider = normalizeProvider(provider);
        return switch (normalizedProvider) {
            case "github" -> pushGitHub(
                    remoteUrl, bundlePath, defaultBranch, featureBranch, baseSha,
                    contentJson, commitMessage, prTitle, prBody, token.trim()
            );
            case "gitlab" -> pushGitLab(
                    remoteUrl, bundlePath, defaultBranch, featureBranch, baseSha,
                    contentJson, commitMessage, prTitle, prBody, token.trim()
            );
            default -> throw unsupportedProvider(provider);
        };
    }

    private FetchedBundle fetchGitHub(String remoteUrl, String bundlePath, String ref, String token) {
        RepoCoords coords = parseGitHubCoords(remoteUrl);
        URI apiBase = githubApiBase(coords.host());
        String commitSha = fetchGitHubCommitSha(apiBase, coords, ref, token);
        String encodedPath = encodePathSegments(bundlePath);
        URI contentsUri = OutboundUrlSafety.resolveEncodedPath(
                apiBase,
                "repos/" + coords.owner() + "/" + coords.repo() + "/contents/" + encodedPath + "?ref="
                        + URLEncoder.encode(commitSha, StandardCharsets.UTF_8)
        );
        JsonNode body = send(contentsUri, "GET", null, token, "Bearer", "application/vnd.github+json", false);
        String json = decodeBase64Content(requireText(body, "content", "GitHub contents"));
        String encoding = text(body, "encoding");
        if (encoding != null && !"base64".equalsIgnoreCase(encoding)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "unsupported GitHub contents encoding: " + encoding
            );
        }
        return new FetchedBundle(json, commitSha, ref);
    }

    private PushPrResult pushGitHub(
            String remoteUrl,
            String bundlePath,
            String defaultBranch,
            String featureBranch,
            String baseSha,
            String contentJson,
            String commitMessage,
            String prTitle,
            String prBody,
            String token
    ) {
        RepoCoords coords = parseGitHubCoords(remoteUrl);
        URI apiBase = githubApiBase(coords.host());
        String tipSha = (baseSha != null && !baseSha.isBlank())
                ? baseSha.trim()
                : fetchGitHubCommitSha(apiBase, coords, defaultBranch, token);

        ObjectNode refBody = objectMapper.createObjectNode();
        refBody.put("ref", "refs/heads/" + featureBranch);
        refBody.put("sha", tipSha);
        URI refsUri = OutboundUrlSafety.resolveEncodedPath(
                apiBase,
                "repos/" + coords.owner() + "/" + coords.repo() + "/git/refs"
        );
        send(refsUri, "POST", refBody.toString(), token, "Bearer", "application/vnd.github+json", false);

        String encodedPath = encodePathSegments(bundlePath);
        URI getFileUri = OutboundUrlSafety.resolveEncodedPath(
                apiBase,
                "repos/" + coords.owner() + "/" + coords.repo() + "/contents/" + encodedPath + "?ref="
                        + URLEncoder.encode(featureBranch, StandardCharsets.UTF_8)
        );
        JsonNode existing = send(getFileUri, "GET", null, token, "Bearer", "application/vnd.github+json", true);
        String existingSha = existing == null ? null : text(existing, "sha");

        ObjectNode putBody = objectMapper.createObjectNode();
        putBody.put("message", commitMessage);
        putBody.put("content", Base64.getEncoder().encodeToString(contentJson.getBytes(StandardCharsets.UTF_8)));
        putBody.put("branch", featureBranch);
        if (existingSha != null && !existingSha.isBlank()) {
            putBody.put("sha", existingSha);
        }
        URI putUri = OutboundUrlSafety.resolveEncodedPath(
                apiBase,
                "repos/" + coords.owner() + "/" + coords.repo() + "/contents/" + encodedPath
        );
        JsonNode putResponse = send(
                putUri, "PUT", putBody.toString(), token, "Bearer", "application/vnd.github+json", false
        );
        String commitSha = text(putResponse.path("commit"), "sha");

        ObjectNode prBodyJson = objectMapper.createObjectNode();
        prBodyJson.put("title", prTitle);
        prBodyJson.put("body", prBody == null ? "" : prBody);
        prBodyJson.put("head", featureBranch);
        prBodyJson.put("base", defaultBranch);
        URI pullsUri = OutboundUrlSafety.resolveEncodedPath(
                apiBase,
                "repos/" + coords.owner() + "/" + coords.repo() + "/pulls"
        );
        JsonNode pr = send(pullsUri, "POST", prBodyJson.toString(), token, "Bearer", "application/vnd.github+json", false);
        String prUrl = text(pr, "html_url");
        Integer prNumber = intOrNull(pr, "number");
        if (prUrl == null || prUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub PR url missing");
        }
        return new PushPrResult(featureBranch, defaultBranch, commitSha, prUrl, prNumber);
    }

    private String fetchGitHubCommitSha(URI apiBase, RepoCoords coords, String ref, String token) {
        URI commitUri = OutboundUrlSafety.resolveEncodedPath(
                apiBase,
                "repos/" + coords.owner() + "/" + coords.repo() + "/commits/"
                        + URLEncoder.encode(ref, StandardCharsets.UTF_8)
        );
        JsonNode body = send(commitUri, "GET", null, token, "Bearer", "application/vnd.github+json", false);
        String sha = text(body, "sha");
        if (sha == null || sha.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub commit sha missing");
        }
        return sha;
    }

    private FetchedBundle fetchGitLab(String remoteUrl, String bundlePath, String ref, String token) {
        GitLabCoords coords = parseGitLabCoords(remoteUrl);
        URI apiBase = OutboundUrlSafety.requireSafePublicHttpsUrl("https://" + coords.host() + "/api/v4");
        String project = URLEncoder.encode(coords.projectPath(), StandardCharsets.UTF_8);
        String filePath = URLEncoder.encode(bundlePath, StandardCharsets.UTF_8);
        URI fileUri = OutboundUrlSafety.resolveEncodedPath(
                apiBase,
                "projects/" + project + "/repository/files/" + filePath + "?ref="
                        + URLEncoder.encode(ref, StandardCharsets.UTF_8)
        );
        JsonNode body = send(fileUri, "GET", null, token, "PRIVATE-TOKEN", null, false);
        String encoding = text(body, "encoding");
        if (encoding != null && !"base64".equalsIgnoreCase(encoding)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "unsupported GitLab file encoding: " + encoding
            );
        }
        String json = decodeBase64Content(requireText(body, "content", "GitLab file"));
        String commitSha = requireText(body, "commit_id", "GitLab commit_id");
        return new FetchedBundle(json, commitSha, ref);
    }

    private PushPrResult pushGitLab(
            String remoteUrl,
            String bundlePath,
            String defaultBranch,
            String featureBranch,
            String baseSha,
            String contentJson,
            String commitMessage,
            String prTitle,
            String prBody,
            String token
    ) {
        GitLabCoords coords = parseGitLabCoords(remoteUrl);
        URI apiBase = OutboundUrlSafety.requireSafePublicHttpsUrl("https://" + coords.host() + "/api/v4");
        String project = URLEncoder.encode(coords.projectPath(), StandardCharsets.UTF_8);
        String filePath = URLEncoder.encode(bundlePath, StandardCharsets.UTF_8);

        ObjectNode branchBody = objectMapper.createObjectNode();
        branchBody.put("branch", featureBranch);
        branchBody.put("ref", (baseSha != null && !baseSha.isBlank()) ? baseSha.trim() : defaultBranch);
        URI branchesUri = OutboundUrlSafety.resolveEncodedPath(
                apiBase,
                "projects/" + project + "/repository/branches"
        );
        send(branchesUri, "POST", branchBody.toString(), token, "PRIVATE-TOKEN", null, false);

        URI getFileUri = OutboundUrlSafety.resolveEncodedPath(
                apiBase,
                "projects/" + project + "/repository/files/" + filePath + "?ref="
                        + URLEncoder.encode(featureBranch, StandardCharsets.UTF_8)
        );
        boolean exists = send(getFileUri, "GET", null, token, "PRIVATE-TOKEN", null, true) != null;

        ObjectNode fileBody = objectMapper.createObjectNode();
        fileBody.put("branch", featureBranch);
        fileBody.put("content", contentJson);
        fileBody.put("commit_message", commitMessage);
        fileBody.put("encoding", "text");
        URI fileUri = OutboundUrlSafety.resolveEncodedPath(
                apiBase,
                "projects/" + project + "/repository/files/" + filePath
        );
        JsonNode fileResponse = send(
                fileUri,
                exists ? "PUT" : "POST",
                fileBody.toString(),
                token,
                "PRIVATE-TOKEN",
                null,
                false
        );
        String commitSha = text(fileResponse, "commit_id");

        ObjectNode mrBody = objectMapper.createObjectNode();
        mrBody.put("source_branch", featureBranch);
        mrBody.put("target_branch", defaultBranch);
        mrBody.put("title", prTitle);
        mrBody.put("description", prBody == null ? "" : prBody);
        URI mrUri = OutboundUrlSafety.resolveEncodedPath(apiBase, "projects/" + project + "/merge_requests");
        JsonNode mr = send(mrUri, "POST", mrBody.toString(), token, "PRIVATE-TOKEN", null, false);
        String prUrl = text(mr, "web_url");
        Integer prNumber = intOrNull(mr, "iid");
        if (prUrl == null || prUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitLab MR url missing");
        }
        return new PushPrResult(featureBranch, defaultBranch, commitSha, prUrl, prNumber);
    }

    private JsonNode send(
            URI uri,
            String method,
            String jsonBody,
            String token,
            String authMode,
            String accept,
            boolean allowNotFound
    ) {
        OutboundUrlSafety.requireSafePublicHttpsUrl(uri.toString());
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "ispf-server");
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
            String verb = method == null ? "GET" : method.toUpperCase(Locale.ROOT);
            if ("GET".equals(verb) || "DELETE".equals(verb)) {
                builder.method(verb, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json");
                builder.method(verb, HttpRequest.BodyPublishers.ofString(jsonBody == null ? "{}" : jsonBody));
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (allowNotFound && status == 404) {
                return null;
            }
            if (status == 401 || status == 403) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "git remote auth failed (HTTP " + status + ")");
            }
            if (status == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "git remote resource not found");
            }
            if (status == 409 || status == 422) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "git remote rejected the request (HTTP " + status + ")"
                );
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
            if (body.isBlank()) {
                return objectMapper.createObjectNode();
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

    private static void requireToken(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "git credential is missing");
        }
    }

    private static String normalizeProvider(String provider) {
        return provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
    }

    private static ResponseStatusException unsupportedProvider(String provider) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported git provider: " + provider);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || field == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static String requireText(JsonNode node, String field, String label) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, label + " missing " + field);
        }
        return value;
    }

    private static Integer intOrNull(JsonNode node, String field) {
        if (node == null || field == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isIntegralNumber()) {
            return null;
        }
        return value.asInt();
    }

    public record FetchedBundle(String contentJson, String commitSha, String ref) {
    }

    public record PushPrResult(
            String branch,
            String baseBranch,
            String commitSha,
            String prUrl,
            Integer prNumber
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("branch", branch);
            map.put("baseBranch", baseBranch);
            map.put("commitSha", commitSha);
            map.put("prUrl", prUrl);
            if (prNumber != null) {
                map.put("prNumber", prNumber);
            }
            return map;
        }
    }

    record RepoCoords(String host, String owner, String repo) {
    }

    record GitLabCoords(String host, String projectPath) {
    }
}
