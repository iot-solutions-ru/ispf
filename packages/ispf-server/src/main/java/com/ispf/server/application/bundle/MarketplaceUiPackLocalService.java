package com.ispf.server.application.bundle;

import com.ispf.server.application.uipack.DropInUiPackLoader;
import com.ispf.server.config.MarketplaceProperties;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Local/dev UI pack marketplace listings (ADR-0054), mirrors symbol-pack local install.
 */
@Service
public class MarketplaceUiPackLocalService {

    private final MarketplaceProperties properties;
    private final DropInUiPackLoader uiPackLoader;
    private final ObjectMapper objectMapper;

    public MarketplaceUiPackLocalService(
            MarketplaceProperties properties,
            DropInUiPackLoader uiPackLoader,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.uiPackLoader = uiPackLoader;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> listLocalPacks() {
        List<Map<String, Object>> packs = new ArrayList<>();
        for (Path dir : candidateRoots()) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (var entries = Files.list(dir)) {
                for (Path child : entries.filter(Files::isDirectory).toList()) {
                    Path listing = child.resolve("listing.manifest.json");
                    Path manifest = child.resolve(DropInUiPackLoader.MANIFEST_FILE);
                    if (!Files.isRegularFile(listing) || !Files.isRegularFile(manifest)) {
                        continue;
                    }
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> listingMap =
                                objectMapper.readValue(Files.readString(listing), Map.class);
                        Map<String, Object> row = new LinkedHashMap<>(listingMap);
                        row.put("artifactKind", "ui-pack");
                        row.put("sourceDir", child.toString());
                        row.put("validationStatus", "OK");
                        String appId = stringValue(listingMap.get("appId"));
                        if (appId.isBlank()) {
                            appId = stringValue(listingMap.get("packId"));
                        }
                        row.put("installed", uiPackLoader.isPackInstalled(appId));
                        packs.add(row);
                    } catch (Exception ignored) {
                        // skip invalid local listings
                    }
                }
            } catch (IOException ignored) {
                // skip unreadable roots
            }
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "OK");
        response.put("packs", packs);
        response.put("count", packs.size());
        return response;
    }

    public Map<String, Object> installLocalPack(String packId) throws Exception {
        if (packId == null || packId.isBlank()) {
            throw new IllegalArgumentException("pack id is required");
        }
        Path source = findSourceDir(packId.trim());
        if (source == null) {
            throw new IllegalArgumentException("Local UI pack not found: " + packId);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> listing =
                objectMapper.readValue(Files.readString(source.resolve("listing.manifest.json")), Map.class);
        String appId = firstNonBlank(stringValue(listing.get("appId")), stringValue(listing.get("packId")), packId);
        Map<String, Object> installed = uiPackLoader.installPackDirectory(source, appId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "OK");
        result.put("artifactKind", "ui-pack");
        result.put("packId", appId);
        result.put("appId", appId);
        result.put("hostedUiUrl", installed.get("hostedUiUrl"));
        result.put("path", installed.get("path"));
        result.put("source", "local");
        return result;
    }

    /** Zip a local pack directory for tests / publish helpers. */
    public byte[] zipPackDirectory(Path sourceDir) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            try (var walk = Files.walk(sourceDir)) {
                for (Path path : walk.filter(Files::isRegularFile).toList()) {
                    String name = sourceDir.relativize(path).toString().replace('\\', '/');
                    zos.putNextEntry(new ZipEntry(name));
                    Files.copy(path, zos);
                    zos.closeEntry();
                }
            }
        }
        return bos.toByteArray();
    }

    private Path findSourceDir(String packId) throws IOException {
        for (Path root : candidateRoots()) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            Path direct = root.resolve(packId);
            if (Files.isRegularFile(direct.resolve(DropInUiPackLoader.MANIFEST_FILE))) {
                return direct;
            }
            try (var entries = Files.list(root)) {
                for (Path child : entries.filter(Files::isDirectory).toList()) {
                    Path listing = child.resolve("listing.manifest.json");
                    if (!Files.isRegularFile(listing)) {
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = objectMapper.readValue(Files.readString(listing), Map.class);
                    if (packId.equals(stringValue(map.get("slug")))
                            || packId.equals(stringValue(map.get("packId")))
                            || packId.equals(stringValue(map.get("appId")))) {
                        return child;
                    }
                }
            }
        }
        return null;
    }

    private List<Path> candidateRoots() {
        List<Path> roots = new ArrayList<>();
        String configured = properties.getLocalBundlesDir();
        if (configured != null && !configured.isBlank()) {
            roots.add(Paths.get(configured.trim()));
        }
        Path cwd = Paths.get(".").toAbsolutePath().normalize();
        roots.add(cwd.resolve("examples"));
        return roots;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
