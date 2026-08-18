package com.ispf.server.application.uipack;

import com.ispf.server.config.UiPackProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ADR-0054: install hosted UI packs under {@link UiPackProperties#getPacksDir()}/&lt;appId&gt;/.
 * Pack content is static assets only — never executed server-side.
 */
@Component
public class DropInUiPackLoader {

    private static final Logger log = LoggerFactory.getLogger(DropInUiPackLoader.class);
    public static final String MANIFEST_FILE = "ui-pack.json";

    private final UiPackProperties properties;
    private final ObjectMapper objectMapper;

    public DropInUiPackLoader(UiPackProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void ensurePacksRoot() {
        try {
            Files.createDirectories(packsRoot());
        } catch (IOException ex) {
            log.warn("Unable to create UI packs directory {}: {}", packsRoot(), ex.getMessage());
        }
    }

    public Path packsRoot() {
        String configured = properties.getPacksDir();
        if (configured == null || configured.isBlank()) {
            return Paths.get("./data/ui-packs").toAbsolutePath().normalize();
        }
        return Paths.get(configured.trim()).toAbsolutePath().normalize();
    }

    public long maxZipBytes() {
        return Math.max(1L, properties.getMaxZipBytes());
    }

    public boolean isPackInstalled(String appId) {
        if (appId == null || appId.isBlank()) {
            return false;
        }
        Path dir = resolvePackDir(appId.trim());
        return dir != null
                && Files.isRegularFile(dir.resolve(MANIFEST_FILE))
                && Files.isRegularFile(dir.resolve(entryFile(dir)));
    }

    public Path resolvePackDir(String appId) {
        if (appId == null || appId.isBlank()) {
            return null;
        }
        Path dir = packsRoot().resolve(appId.trim()).normalize();
        if (!dir.startsWith(packsRoot())) {
            return null;
        }
        return dir;
    }

    public synchronized Map<String, Object> installZipArchive(byte[] zipBytes, String expectedAppId)
            throws IOException {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new IllegalArgumentException("ui-pack zip is empty");
        }
        if (zipBytes.length > maxZipBytes()) {
            throw new IllegalArgumentException(
                    "ui-pack zip exceeds max size (" + maxZipBytes() + " bytes)"
            );
        }
        Path tempDir = Files.createTempDirectory("ispf-ui-pack-");
        try {
            unzip(zipBytes, tempDir);
            Path packRoot = findPackRoot(tempDir);
            return installPackDirectory(packRoot, expectedAppId);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    public synchronized Map<String, Object> installPackDirectory(Path sourceDir, String expectedAppId)
            throws IOException {
        Path manifestPath = sourceDir.resolve(MANIFEST_FILE);
        if (!Files.isRegularFile(manifestPath)) {
            throw new IllegalArgumentException("ui pack directory missing ui-pack.json");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> manifest = objectMapper.readValue(Files.readString(manifestPath), Map.class);
        String appId = firstNonBlank(stringValue(manifest.get("appId")), stringValue(manifest.get("id")));
        if (appId.isBlank()) {
            throw new IllegalArgumentException("ui-pack.json missing appId/id");
        }
        if (!isSafeAppId(appId)) {
            throw new IllegalArgumentException("Invalid ui-pack appId: " + appId);
        }
        if (expectedAppId != null && !expectedAppId.isBlank() && !expectedAppId.trim().equals(appId)) {
            throw new IllegalArgumentException("Pack appId mismatch: expected " + expectedAppId + " got " + appId);
        }
        String entry = stringValue(manifest.get("entry"));
        if (entry.isBlank()) {
            entry = "index.html";
            manifest.put("entry", entry);
        }
        if (entry.contains("..") || entry.startsWith("/") || entry.startsWith("\\")) {
            throw new IllegalArgumentException("ui-pack entry must be a relative file path");
        }
        if (!Files.isRegularFile(sourceDir.resolve(entry))) {
            throw new IllegalArgumentException("ui-pack entry file missing: " + entry);
        }

        Path targetDir = resolvePackDir(appId);
        if (targetDir == null) {
            throw new IllegalArgumentException("Invalid pack appId path: " + appId);
        }
        if (Files.exists(targetDir)) {
            deleteRecursively(targetDir);
        }
        Files.createDirectories(targetDir);
        copyTree(sourceDir, targetDir);
        // Rewrite manifest with normalized fields for serving.
        Map<String, Object> stored = new LinkedHashMap<>(manifest);
        stored.put("appId", appId);
        stored.putIfAbsent("id", appId);
        stored.put("entry", entry);
        stored.putIfAbsent("basePath", "/apps/" + appId + "/");
        Files.writeString(targetDir.resolve(MANIFEST_FILE), objectMapper.writeValueAsString(stored));
        log.info("Installed UI pack {} → {}", appId, targetDir);
        return packSummary(targetDir, stored);
    }

    public List<Map<String, Object>> listInstalledPacks() {
        List<Map<String, Object>> rows = new ArrayList<>();
        Path root = packsRoot();
        if (!Files.isDirectory(root)) {
            return rows;
        }
        try (var entries = Files.list(root)) {
            for (Path dir : entries.filter(Files::isDirectory).sorted().toList()) {
                Path manifestPath = dir.resolve(MANIFEST_FILE);
                if (!Files.isRegularFile(manifestPath)) {
                    continue;
                }
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> manifest =
                            objectMapper.readValue(Files.readString(manifestPath), Map.class);
                    rows.add(packSummary(dir, manifest));
                } catch (Exception ex) {
                    log.warn("Skipping corrupt UI pack {}: {}", dir, ex.getMessage());
                }
            }
        } catch (IOException ex) {
            log.warn("Failed to list UI packs: {}", ex.getMessage());
        }
        return rows;
    }

    public Map<String, Object> getPackDetail(String appId) throws IOException {
        Path packDir = requireInstalledDir(appId);
        @SuppressWarnings("unchecked")
        Map<String, Object> manifest =
                objectMapper.readValue(Files.readString(packDir.resolve(MANIFEST_FILE)), Map.class);
        Map<String, Object> detail = new LinkedHashMap<>(packSummary(packDir, manifest));
        detail.put("manifest", manifest);
        return detail;
    }

    public Path resolveAsset(String appId, String relativePath) throws IOException {
        Path packDir = requireInstalledDir(appId);
        String relative = relativePath == null || relativePath.isBlank() ? entryFile(packDir) : relativePath;
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        Path asset = packDir.resolve(relative).normalize();
        if (!asset.startsWith(packDir)) {
            throw new IllegalArgumentException("Path escapes UI pack root");
        }
        if (Files.isRegularFile(asset)) {
            return asset;
        }
        // SPA fallback: directories and extension-less routes → entry html
        if (!hasFileExtension(relative) || Files.isDirectory(asset)) {
            Path entry = packDir.resolve(entryFile(packDir)).normalize();
            if (entry.startsWith(packDir) && Files.isRegularFile(entry)) {
                return entry;
            }
        }
        return null;
    }

    public Map<String, Object> packSummary(String appId) {
        Path dir = resolvePackDir(appId);
        if (dir == null || !Files.isRegularFile(dir.resolve(MANIFEST_FILE))) {
            return Map.of();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> manifest =
                    objectMapper.readValue(Files.readString(dir.resolve(MANIFEST_FILE)), Map.class);
            return packSummary(dir, manifest);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private Path requireInstalledDir(String appId) {
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("appId is required");
        }
        Path packDir = resolvePackDir(appId.trim());
        if (packDir == null || !Files.isRegularFile(packDir.resolve(MANIFEST_FILE))) {
            throw new IllegalArgumentException("UI pack is not installed: " + appId);
        }
        return packDir;
    }

    private String entryFile(Path packDir) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> manifest =
                    objectMapper.readValue(Files.readString(packDir.resolve(MANIFEST_FILE)), Map.class);
            String entry = stringValue(manifest.get("entry"));
            return entry.isBlank() ? "index.html" : entry;
        } catch (Exception ex) {
            return "index.html";
        }
    }

    private Map<String, Object> packSummary(Path packDir, Map<String, Object> manifest) {
        String appId = firstNonBlank(stringValue(manifest.get("appId")), stringValue(manifest.get("id")));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("packId", appId);
        row.put("appId", appId);
        row.put("version", manifest.get("version"));
        row.put("entry", manifest.getOrDefault("entry", "index.html"));
        row.put("basePath", manifest.getOrDefault("basePath", "/apps/" + appId + "/"));
        row.put("hostedUiUrl", "/apps/" + appId + "/");
        row.put("path", packDir.toString());
        row.put("installed", true);
        return row;
    }

    private Path findPackRoot(Path extracted) throws IOException {
        if (Files.isRegularFile(extracted.resolve(MANIFEST_FILE))) {
            return extracted;
        }
        try (var entries = Files.list(extracted)) {
            for (Path child : entries.filter(Files::isDirectory).toList()) {
                if (Files.isRegularFile(child.resolve(MANIFEST_FILE))) {
                    return child;
                }
            }
        }
        throw new IllegalArgumentException("ui-pack.zip missing ui-pack.json");
    }

    private static void unzip(byte[] zipBytes, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path out = targetDir.resolve(entry.getName()).normalize();
                if (!out.startsWith(targetDir)) {
                    throw new IOException("Zip entry escapes target: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void copyTree(Path source, Path target) throws IOException {
        try (var walk = Files.walk(source)) {
            for (Path path : walk.toList()) {
                Path relative = source.relativize(path);
                Path dest = target.resolve(relative.toString()).normalize();
                if (!dest.startsWith(target)) {
                    throw new IOException("Refusing to copy outside pack dir: " + relative);
                }
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            List<Path> paths = walk.sorted((a, b) -> b.compareTo(a)).toList();
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static boolean isSafeAppId(String appId) {
        if (appId == null || appId.isBlank() || appId.length() > 64) {
            return false;
        }
        if (HostedUiPackOperatorAgentInjector.PLATFORM_APP_ID.equalsIgnoreCase(appId)) {
            return false;
        }
        for (int i = 0; i < appId.length(); i++) {
            char c = appId.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.')) {
                return false;
            }
        }
        return !appId.contains("..");
    }

    private static boolean hasFileExtension(String path) {
        int slash = path.lastIndexOf('/');
        String last = slash >= 0 ? path.substring(slash + 1) : path;
        return last.contains(".");
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        return b == null ? "" : b.trim();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
