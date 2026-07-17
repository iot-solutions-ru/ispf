package com.ispf.server.scada.symbol;

import com.ispf.server.config.ScadaSymbolPackProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
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
 * BL-185: install and list SCADA symbol packs under {@link ScadaSymbolPackProperties#getSymbolPacksDir()}.
 */
@Component
public class DropInSymbolPackLoader {

    private static final Logger log = LoggerFactory.getLogger(DropInSymbolPackLoader.class);
    private static final String MANIFEST_FILE = "manifest.json";

    private final ScadaSymbolPackProperties properties;
    private final ObjectMapper objectMapper;

    public DropInSymbolPackLoader(ScadaSymbolPackProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void ensurePacksRoot() {
        try {
            Files.createDirectories(packsRoot());
        } catch (IOException ex) {
            log.warn("Unable to create symbol packs directory {}: {}", packsRoot(), ex.getMessage());
        }
    }

    public Path packsRoot() {
        String configured = properties.getSymbolPacksDir();
        if (configured == null || configured.isBlank()) {
            return Paths.get("./data/symbol-packs").toAbsolutePath().normalize();
        }
        return Paths.get(configured.trim()).toAbsolutePath().normalize();
    }

    public boolean isPackInstalled(String packId) {
        if (packId == null || packId.isBlank()) {
            return false;
        }
        Path dir = packsRoot().resolve(packId.trim()).normalize();
        return dir.startsWith(packsRoot()) && Files.isRegularFile(dir.resolve(MANIFEST_FILE));
    }

    public synchronized Map<String, Object> installZipArchive(byte[] zipBytes, String expectedPackId) throws IOException {
        Path tempDir = Files.createTempDirectory("ispf-symbol-pack-");
        try {
            unzip(zipBytes, tempDir);
            Path packRoot = findPackRoot(tempDir);
            return installPackDirectory(packRoot, expectedPackId);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    public synchronized Map<String, Object> installPackDirectory(Path sourceDir) throws IOException {
        return installPackDirectory(sourceDir, null);
    }

    public synchronized Map<String, Object> installPackDirectory(Path sourceDir, String expectedPackId)
            throws IOException {
        Path manifestPath = sourceDir.resolve(MANIFEST_FILE);
        if (!Files.isRegularFile(manifestPath)) {
            throw new IllegalArgumentException("symbol pack directory missing manifest.json");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> manifest = objectMapper.readValue(Files.readString(manifestPath), Map.class);
        String packId = stringValue(manifest.get("id"));
        if (packId.isBlank()) {
            throw new IllegalArgumentException("manifest.json missing id");
        }
        if (expectedPackId != null && !expectedPackId.isBlank() && !expectedPackId.trim().equals(packId)) {
            throw new IllegalArgumentException("Pack id mismatch: expected " + expectedPackId + " got " + packId);
        }
        validateManifest(manifest, sourceDir);

        Path targetDir = packsRoot().resolve(packId).normalize();
        if (!targetDir.startsWith(packsRoot())) {
            throw new IllegalArgumentException("Invalid pack id path: " + packId);
        }
        Files.createDirectories(targetDir);
        copyTree(sourceDir, targetDir);
        log.info("Installed symbol pack {} → {}", packId, targetDir);
        return packSummary(targetDir, manifest);
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
                    Map<String, Object> manifest = objectMapper.readValue(Files.readString(manifestPath), Map.class);
                    rows.add(packSummary(dir, manifest));
                } catch (Exception ex) {
                    log.warn("Skipping corrupt symbol pack {}: {}", dir, ex.getMessage());
                }
            }
        } catch (IOException ex) {
            log.warn("Failed to list symbol packs: {}", ex.getMessage());
        }
        return rows;
    }

    public Map<String, Object> getPackDetail(String packId) throws IOException {
        if (packId == null || packId.isBlank()) {
            throw new IllegalArgumentException("packId is required");
        }
        Path packDir = packsRoot().resolve(packId.trim()).normalize();
        if (!packDir.startsWith(packsRoot()) || !Files.isRegularFile(packDir.resolve(MANIFEST_FILE))) {
            throw new IllegalArgumentException("Symbol pack is not installed: " + packId);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> manifest = objectMapper.readValue(Files.readString(packDir.resolve(MANIFEST_FILE)), Map.class);
        Map<String, Object> detail = new LinkedHashMap<>(packSummary(packDir, manifest));
        List<Map<String, Object>> categories = new ArrayList<>();
        Object cats = manifest.get("categories");
        if (cats instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> cat)) {
                    continue;
                }
                String file = stringValue(cat.get("file"));
                Map<String, Object> category = new LinkedHashMap<>();
                category.put("id", cat.get("id"));
                category.put("file", file);
                category.put("count", cat.get("count"));
                if (!file.isBlank() && Files.isRegularFile(packDir.resolve(file))) {
                    Object symbols = objectMapper.readValue(Files.readString(packDir.resolve(file)), Object.class);
                    category.put("symbols", symbols);
                } else {
                    category.put("symbols", List.of());
                }
                categories.add(category);
            }
        }
        detail.put("categories", categories);
        detail.put("manifest", manifest);
        return detail;
    }

    private Map<String, Object> packSummary(Path packDir, Map<String, Object> manifest) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("packId", manifest.get("id"));
        row.put("version", manifest.get("version"));
        row.put("license", manifest.get("license"));
        row.put("totalSymbols", manifest.get("totalSymbols"));
        row.put("categories", manifest.get("categories"));
        row.put("path", packDir.toString());
        row.put("installed", true);
        return row;
    }

    private void validateManifest(Map<String, Object> manifest, Path sourceDir) {
        Object cats = manifest.get("categories");
        if (!(cats instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("manifest.json must declare categories[]");
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> cat)) {
                continue;
            }
            String file = stringValue(cat.get("file"));
            if (file.isBlank() || !Files.isRegularFile(sourceDir.resolve(file))) {
                throw new IllegalArgumentException("manifest category file missing: " + file);
            }
        }
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
        throw new IllegalArgumentException("symbol-pack.zip missing manifest.json");
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

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
