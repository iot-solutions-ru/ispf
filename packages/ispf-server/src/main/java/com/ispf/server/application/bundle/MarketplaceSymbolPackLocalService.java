package com.ispf.server.application.bundle;

import com.ispf.server.scada.symbol.DropInSymbolPackLoader;
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
 * BL-185: local symbol-pack marketplace install from repo examples (dev/lab).
 */
@Service
public class MarketplaceSymbolPackLocalService {

    private final ObjectMapper objectMapper;
    private final DropInSymbolPackLoader symbolPackLoader;

    public MarketplaceSymbolPackLocalService(
            ObjectMapper objectMapper,
            DropInSymbolPackLoader symbolPackLoader
    ) {
        this.objectMapper = objectMapper;
        this.symbolPackLoader = symbolPackLoader;
    }

    public Map<String, Object> listLocalPacks() {
        Path root = resolveLocalRoot();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("source", "local");
        response.put("artifactKind", "symbol-pack");
        response.put("root", root != null ? root.toString() : "");
        if (root == null || !Files.isDirectory(root)) {
            response.put("status", "OK");
            response.put("count", 0);
            response.put("packs", List.of());
            return response;
        }

        List<Map<String, Object>> packs = new ArrayList<>();
        try {
            for (Path packDir : listPackDirectories(root)) {
                packs.add(loadPackListing(packDir));
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to scan local symbol packs: " + ex.getMessage(), ex);
        }

        response.put("status", "OK");
        response.put("count", packs.size());
        response.put("packs", packs);
        return response;
    }

    public Map<String, Object> installLocalPack(String packId) throws Exception {
        if (packId == null || packId.isBlank()) {
            throw new IllegalArgumentException("pack id is required");
        }
        Path root = resolveLocalRoot();
        if (root == null || !Files.isDirectory(root)) {
            throw new IllegalStateException("Local symbol pack directory is not configured");
        }

        ResolvedListing resolved = resolveListing(root, packId.trim());
        if (!"OK".equals(resolved.row().get("validationStatus"))) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("status", "ERROR");
            error.put("packId", packId);
            error.put("validationStatus", resolved.row().get("validationStatus"));
            error.put("errors", resolved.row().getOrDefault("errors", List.of()));
            return error;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> listing = objectMapper.readValue(Files.readString(resolved.listingPath()), Map.class);
        String pricing = stringValue(listing.getOrDefault("pricing", "free"));
        if (!"free".equalsIgnoreCase(pricing)) {
            throw new IllegalArgumentException("Local install supports free symbol packs only");
        }

        Map<String, Object> installed;
        if (resolved.zipPath() != null) {
            byte[] zipBytes = Files.readAllBytes(resolved.zipPath());
            installed = symbolPackLoader.installZipArchive(zipBytes, stringValue(listing.get("packId")));
        } else {
            installed = symbolPackLoader.installPackDirectory(resolved.packDir(), stringValue(listing.get("packId")));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "OK");
        result.put("source", "local-marketplace");
        result.put("artifactKind", "symbol-pack");
        result.put("action", "install");
        result.put("packId", listing.get("packId"));
        result.put("slug", listing.get("slug"));
        result.put("version", listing.get("latestVersion"));
        result.put("symbolCount", installed.get("totalSymbols"));
        result.put("path", installed.get("path"));
        result.put("listingPath", resolved.listingPath().toString());
        return result;
    }

    private record ResolvedListing(Map<String, Object> row, Path listingPath, Path packDir, Path zipPath) {
    }

    private ResolvedListing resolveListing(Path root, String packId) throws Exception {
        for (Path packDir : listPackDirectories(root)) {
            Map<String, Object> row = loadPackListing(packDir);
            String slug = stringValue(row.get("slug"));
            String directory = stringValue(row.get("directory"));
            if (packId.equals(slug) || packId.equals(directory) || packId.equals(stringValue(row.get("packId")))) {
                return new ResolvedListing(
                        row,
                        packDir.resolve("listing.manifest.json"),
                        packDir,
                        resolveZipArtifact(packDir, row)
                );
            }
        }
        throw new IllegalArgumentException("Unknown local symbol pack: " + packId);
    }

    private List<Path> listPackDirectories(Path root) throws Exception {
        List<Path> dirs = new ArrayList<>();
        if (Files.isRegularFile(root.resolve("listing.manifest.json"))) {
            dirs.add(root);
            return dirs;
        }
        try (Stream<Path> children = Files.list(root)) {
            for (Path dir : children.filter(Files::isDirectory).sorted().toList()) {
                String name = dir.getFileName().toString();
                if (name.startsWith("marketplace-symbol-")
                        && Files.isRegularFile(dir.resolve("listing.manifest.json"))) {
                    dirs.add(dir);
                }
            }
        }
        return dirs;
    }

    private Path resolveZipArtifact(Path dir, Map<String, Object> row) {
        String artifact = stringValue(row.get("bundleArtifact"));
        if (!artifact.isBlank() && Files.isRegularFile(dir.resolve(artifact))) {
            return dir.resolve(artifact);
        }
        try (Stream<Path> zips = Files.list(dir)) {
            return zips
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".zip"))
                    .findFirst()
                    .orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> loadPackListing(Path dir) throws Exception {
        Path listingPath = dir.resolve("listing.manifest.json");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("directory", dir.getFileName().toString());

        if (!Files.isRegularFile(listingPath)) {
            row.put("validationStatus", "MISSING_LISTING");
            row.put("errors", List.of("listing.manifest.json not found"));
            return row;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> listing = objectMapper.readValue(Files.readString(listingPath), Map.class);
        List<String> errors = validateListing(listing, dir);
        row.put("slug", listing.get("slug"));
        row.put("title", listing.get("title"));
        row.put("description", listing.get("description"));
        row.put("packId", listing.get("packId"));
        row.put("artifactKind", listing.getOrDefault("artifactKind", "symbol-pack"));
        row.put("pricing", listing.getOrDefault("pricing", "free"));
        row.put("latestVersion", listing.get("latestVersion"));
        row.put("version", listing.get("latestVersion"));
        row.put("minIspfVersion", listing.get("minIspfVersion"));
        row.put("vendorName", listing.get("vendorName"));
        row.put("vendorLegalName", listing.get("vendorLegalName"));
        row.put("license", listing.getOrDefault("license", "Apache-2.0"));
        row.put("symbolCount", listing.get("symbolCount"));
        row.put("categories", listing.get("categories"));
        row.put("tags", listing.get("tags"));
        row.put("bundleArtifact", listing.get("bundleArtifact"));
        row.put("installed", symbolPackLoader.isPackInstalled(stringValue(listing.get("packId"))));
        row.put("listingPath", listingPath.toString());
        row.put("validationStatus", errors.isEmpty() ? "OK" : "INVALID");
        if (!errors.isEmpty()) {
            row.put("errors", errors);
        }
        return row;
    }

    private List<String> validateListing(Map<String, Object> listing, Path dir) {
        List<String> errors = new ArrayList<>();
        for (String key : List.of("slug", "title", "description", "packId", "latestVersion")) {
            Object value = listing.get(key);
            if (value == null || String.valueOf(value).isBlank()) {
                errors.add("listing missing " + key);
            }
        }
        if (!"symbol-pack".equalsIgnoreCase(stringValue(listing.get("artifactKind")))) {
            errors.add("listing artifactKind must be symbol-pack");
        }
        String artifact = stringValue(listing.get("bundleArtifact"));
        boolean hasZip = !artifact.isBlank() && Files.isRegularFile(dir.resolve(artifact));
        if (!hasZip) {
            try (Stream<Path> zips = Files.list(dir)) {
                hasZip = zips.anyMatch(
                        path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".zip")
                );
            } catch (Exception ignored) {
                hasZip = false;
            }
        }
        boolean hasManifest = Files.isRegularFile(dir.resolve("manifest.json"));
        if (!hasZip && !hasManifest) {
            errors.add("missing symbol pack zip or manifest.json directory");
        }
        return errors;
    }

    private Path resolveLocalRoot() {
        Path cwd = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (int depth = 0; depth <= 4; depth++) {
            Path examples = cwd.resolve("examples");
            if (Files.isDirectory(examples) && hasMarketplaceSymbolPack(examples)) {
                return examples;
            }
            Path parent = cwd.getParent();
            if (parent == null) {
                break;
            }
            cwd = parent;
        }
        Path examples = Paths.get("examples");
        return Files.isDirectory(examples) && hasMarketplaceSymbolPack(examples) ? examples : null;
    }

    private static boolean hasMarketplaceSymbolPack(Path examplesDir) {
        try (Stream<Path> dirs = Files.list(examplesDir)) {
            return dirs.anyMatch(path -> Files.isDirectory(path)
                    && path.getFileName().toString().startsWith("marketplace-symbol-")
                    && Files.isRegularFile(path.resolve("listing.manifest.json")));
        } catch (Exception ex) {
            return false;
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
