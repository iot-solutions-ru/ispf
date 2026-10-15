package com.ispf.server.platform.analytics.pack;

import com.ispf.analytics.spi.AnalyticsFunctionProvider;
import com.ispf.server.config.AnalyticsPackProperties;
import com.ispf.server.config.CommercialLicenseProperties;
import com.ispf.server.driver.pack.DriverPackLicenseClaims;
import com.ispf.server.license.CommercialLicenseException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import java.util.Locale;
/**
 * Loads Tier C analytics packs from {@link AnalyticsPackProperties#getPacksDir()} (BL-216).
 *
 * <p><strong>Security:</strong> JARs are loaded in-process via {@link URLClassLoader}. Treat pack
 * install as admin-equivalent code execution. Production must set {@code ispf.license.enforce=true}
 * (and signed bundles) so unsigned/open packs cannot load. Future hardening: isolate the pack
 * classloader parent / run packs out-of-process.
 */
@Component
@Order(100)
public class DropInAnalyticsPackLoader {

    private static final Logger log = LoggerFactory.getLogger(DropInAnalyticsPackLoader.class);
    private static final String MANIFEST_FILE = "analytics-pack.json";

    private final AnalyticsPackProperties packProperties;
    private final AnalyticsPackLoader analyticsPackLoader;
    private final ObjectMapper objectMapper;
    private final CommercialLicenseProperties licenseProperties;
    private final AnalyticsPackLicenseSigner licenseSigner;
    private final Map<String, String> loadFailures = new LinkedHashMap<>();

    public DropInAnalyticsPackLoader(
            AnalyticsPackProperties packProperties,
            AnalyticsPackLoader analyticsPackLoader,
            ObjectMapper objectMapper,
            CommercialLicenseProperties licenseProperties,
            AnalyticsPackLicenseSigner licenseSigner
    ) {
        this.packProperties = packProperties;
        this.analyticsPackLoader = analyticsPackLoader;
        this.objectMapper = objectMapper;
        this.licenseProperties = licenseProperties;
        this.licenseSigner = licenseSigner;
    }

    @PostConstruct
    public void loadDropInPacksOnStartup() {
        Path packsRoot = packsRoot();
        if (!Files.isDirectory(packsRoot)) {
            log.debug("Analytics packs directory not found: {}", packsRoot);
            return;
        }
        try (var entries = Files.list(packsRoot)) {
            entries.filter(Files::isDirectory).forEach(this::loadPackDirectory);
        } catch (IOException ex) {
            log.warn("Failed to scan analytics packs directory {}: {}", packsRoot, ex.getMessage());
        }
    }

    public synchronized List<String> installZipArchive(byte[] zipBytes, String expectedPackId) throws IOException {
        Path tempDir = Files.createTempDirectory("ispf-analytics-pack-");
        try {
            unzip(zipBytes, tempDir);
            Path manifestPath = tempDir.resolve(MANIFEST_FILE);
            if (!Files.isRegularFile(manifestPath)) {
                throw new IllegalArgumentException("analytics-pack.zip missing analytics-pack.json");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> root = objectMapper.readValue(manifestPath.toFile(), Map.class);
            AnalyticsPackManifest manifest = AnalyticsPackManifest.fromMap(root);
            if (manifest == null) {
                throw new IllegalArgumentException("Invalid analytics-pack.json");
            }
            if (expectedPackId != null && !expectedPackId.isBlank()
                    && !expectedPackId.trim().equals(manifest.packId())) {
                throw new IllegalArgumentException(
                        "Pack id mismatch: expected " + expectedPackId + " got " + manifest.packId()
                );
            }

            Path jarPath = tempDir.resolve(manifest.jarFile()).normalize();
            if (Files.isRegularFile(jarPath) && manifest.license() == null
                    && licenseSigner.isConfigured()
                    && !isOpenLicenseType(manifest.licenseType())) {
                @SuppressWarnings("unchecked")
                Map<String, Object> signedRoot = licenseSigner.signManifestIfNeeded(
                        manifest.packId(),
                        objectMapper.readValue(manifestPath.toFile(), Map.class),
                        jarPath
                );
                objectMapper.writeValue(manifestPath.toFile(), signedRoot);
                manifest = AnalyticsPackManifest.fromMap(signedRoot);
            }

            Path targetDir = packsRoot().resolve(manifest.packId()).normalize();
            if (!targetDir.startsWith(packsRoot())) {
                throw new IllegalArgumentException("Invalid pack id path: " + manifest.packId());
            }
            Files.createDirectories(targetDir);
            copyTree(tempDir, targetDir);
            return loadPackDirectory(targetDir);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    public synchronized List<String> installPackDirectory(Path sourceDir) throws IOException {
        Path manifestPath = sourceDir.resolve(MANIFEST_FILE);
        if (!Files.isRegularFile(manifestPath)) {
            throw new IllegalArgumentException("analytics-pack directory missing analytics-pack.json");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> root = objectMapper.readValue(manifestPath.toFile(), Map.class);
        AnalyticsPackManifest manifest = AnalyticsPackManifest.fromMap(root);
        if (manifest == null) {
            throw new IllegalArgumentException("Invalid analytics-pack.json");
        }
        Path targetDir = packsRoot().resolve(manifest.packId()).normalize();
        if (!targetDir.startsWith(packsRoot())) {
            throw new IllegalArgumentException("Invalid pack id path: " + manifest.packId());
        }
        Files.createDirectories(targetDir);
        copyTree(sourceDir, targetDir);
        return loadPackDirectory(targetDir);
    }

    public synchronized List<String> loadPackDirectory(Path packDir) {
        Path manifestPath = packDir.resolve(MANIFEST_FILE);
        if (!Files.isRegularFile(manifestPath)) {
            return List.of();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> root = objectMapper.readValue(manifestPath.toFile(), Map.class);
            AnalyticsPackManifest manifest = AnalyticsPackManifest.fromMap(root);
            if (manifest == null) {
                String error = "Invalid analytics pack manifest";
                recordLoadFailure(packDir.getFileName().toString(), error);
                log.error("Analytics pack failed in {}: {}", packDir, error);
                return List.of();
            }
            Path jarPath = packDir.resolve(manifest.jarFile()).normalize();
            if (!jarPath.startsWith(packDir.normalize()) || !Files.isRegularFile(jarPath)) {
                String error = "JAR not found: " + jarPath;
                recordLoadFailure(manifest.packId(), error);
                log.error("Analytics pack {} failed: {}", manifest.packId(), error);
                return List.of();
            }
            if (!verifyPackLicense(manifest, jarPath)) {
                return List.of();
            }
            List<String> helpers = registerJar(manifest.packId(), jarPath);
            loadFailures.remove(manifest.packId());
            return helpers;
        } catch (Exception ex) {
            String error = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? ex.getClass().getSimpleName()
                    : ex.getMessage();
            recordLoadFailure(packDir.getFileName().toString(), error);
            log.error("Analytics pack failed in {}: {}", packDir, error);
            return List.of();
        }
    }

    public Set<String> installedPackIds() {
        Path packsRoot = packsRoot();
        if (!Files.isDirectory(packsRoot)) {
            return Set.of();
        }
        Set<String> packIds = new LinkedHashSet<>();
        try (var entries = Files.list(packsRoot)) {
            for (Path dir : entries.filter(Files::isDirectory).toList()) {
                if (Files.isRegularFile(dir.resolve(MANIFEST_FILE))) {
                    packIds.add(dir.getFileName().toString());
                }
            }
        } catch (IOException ex) {
            log.warn("Failed to list installed analytics packs: {}", ex.getMessage());
        }
        return Set.copyOf(packIds);
    }

    public boolean isPackInstalled(String packId) {
        if (packId == null || packId.isBlank()) {
            return false;
        }
        return Files.isRegularFile(packsRoot().resolve(packId.trim()).resolve(MANIFEST_FILE));
    }

    public synchronized List<Map<String, Object>> listInstalledPacks() {
        Path packsRoot = packsRoot();
        if (!Files.isDirectory(packsRoot)) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        try (var entries = Files.list(packsRoot)) {
            for (Path dir : entries.filter(Files::isDirectory).sorted().toList()) {
                Path manifestPath = dir.resolve(MANIFEST_FILE);
                if (!Files.isRegularFile(manifestPath)) {
                    continue;
                }
                rows.add(catalogRow(dir, manifestPath));
            }
        } catch (IOException ex) {
            log.error("Failed to list installed analytics packs: {}", ex.getMessage());
        }
        return rows;
    }

    private Map<String, Object> catalogRow(Path dir, Path manifestPath) {
        String dirName = dir.getFileName().toString();
        Map<String, Object> row = new LinkedHashMap<>();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> root = objectMapper.readValue(manifestPath.toFile(), Map.class);
            AnalyticsPackManifest manifest = AnalyticsPackManifest.fromMap(root);
            if (manifest == null) {
                row.put("packId", dirName);
                row.put("status", "failed");
                row.put("error", failureOr(dirName, "Invalid analytics pack manifest"));
                return row;
            }
            row.put("packId", manifest.packId());
            row.put("version", manifest.version());
            row.put("functions", manifest.functions());
            row.put("licenseType", manifest.licenseType());
            row.put("minPlatformVersion", manifest.minPlatformVersion());
            Path jarPath = dir.resolve(manifest.jarFile()).normalize();
            if (!jarPath.startsWith(dir.normalize()) || !Files.isRegularFile(jarPath)) {
                row.put("helpers", List.of());
                row.put("status", "failed");
                row.put("error", failureOr(manifest.packId(), "JAR not found: " + jarPath));
                return row;
            }
            String failure = loadFailures.get(manifest.packId());
            if (failure == null) {
                failure = loadFailures.get(dirName);
            }
            row.put("helpers", analyticsPackLoader.helpersForPack(manifest.packId()));
            row.put("status", failure == null ? "loaded" : "failed");
            row.put("error", failure == null ? "" : failure);
            return row;
        } catch (Exception ex) {
            String error = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? ex.getClass().getSimpleName()
                    : ex.getMessage();
            row.put("packId", dirName);
            row.put("status", "failed");
            row.put("error", failureOr(dirName, error));
            return row;
        }
    }

    private void recordLoadFailure(String packId, String error) {
        loadFailures.put(packId, error);
    }

    private String failureOr(String packId, String fallback) {
        String recorded = loadFailures.get(packId);
        return recorded == null || recorded.isBlank() ? fallback : recorded;
    }

    public synchronized Map<String, Object> uninstallPack(String packId) throws IOException {
        if (packId == null || packId.isBlank()) {
            throw new IllegalArgumentException("packId is required");
        }
        String normalized = packId.trim();
        Path packDir = packsRoot().resolve(normalized).normalize();
        if (!packDir.startsWith(packsRoot()) || !Files.isDirectory(packDir)) {
            throw new IllegalArgumentException("Analytics pack is not installed: " + normalized);
        }
        List<String> removedHelpers = new ArrayList<>(analyticsPackLoader.helpersForPack(normalized));
        analyticsPackLoader.unregisterPack(normalized);
        deleteRecursively(packDir);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "OK");
        result.put("action", "uninstall");
        result.put("packId", normalized);
        result.put("removedHelpers", removedHelpers);
        log.info("Analytics drop-in pack removed: {} helpers={}", normalized, removedHelpers);
        return result;
    }

    private boolean verifyPackLicense(AnalyticsPackManifest manifest, Path jarPath) {
        DriverPackLicenseClaims claims = DriverPackLicenseClaims.fromMap(manifest.license());
        if (claims != null) {
            try {
                licenseSigner.verify(manifest.packId(), jarPath, claims);
                return true;
            } catch (CommercialLicenseException ex) {
                String error = ex.getMessage() == null || ex.getMessage().isBlank()
                        ? ex.getClass().getSimpleName()
                        : ex.getMessage();
                recordLoadFailure(manifest.packId(), error);
                log.error("Analytics pack {} failed: {}", manifest.packId(), error);
                return false;
            }
        }
        if (!isOpenLicenseType(manifest.licenseType())) {
            if (licenseProperties.isEnforce()) {
                String error = "Commercial license required when enforce=true";
                recordLoadFailure(manifest.packId(), error);
                log.error("Analytics pack {} failed: {}", manifest.packId(), error);
                return false;
            }
            log.warn("Loading commercial analytics pack {} without license (enforce=false)", manifest.packId());
        }
        return true;
    }

    private static boolean isOpenLicenseType(String licenseType) {
        if (licenseType == null || licenseType.isBlank()) {
            return true;
        }
        String normalized = licenseType.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("apache-2.0")
                || normalized.equals("mit")
                || normalized.equals("bsd-3-clause")
                || normalized.equals("public-domain");
    }

    private List<String> registerJar(String packId, Path jarPath) throws IOException {
        URL jarUrl = jarPath.toUri().toURL();
        URLClassLoader classLoader = new URLClassLoader(
                new URL[] {jarUrl},
                AnalyticsFunctionProvider.class.getClassLoader()
        );
        List<String> loaded = new ArrayList<>();
        try {
            ServiceLoader<AnalyticsFunctionProvider> providers =
                    ServiceLoader.load(AnalyticsFunctionProvider.class, classLoader);
            for (AnalyticsFunctionProvider provider : providers) {
                analyticsPackLoader.register(provider);
                String helper = provider.getDescriptor() != null ? provider.getDescriptor().helper() : null;
                if (helper != null && !helper.isBlank()) {
                    loaded.add(helper.trim());
                }
            }
        } catch (LinkageError ex) {
            throw new IllegalStateException(
                    "Failed to load analytics pack " + packId + ": " + ex.getMessage()
                            + " (check pack targets public Tier C SPI base classes)",
                    ex
            );
        }
        if (loaded.isEmpty()) {
            log.warn("Analytics pack {} registered no SPI providers from {}", packId, jarPath);
        } else {
            log.info("Analytics drop-in pack loaded: {} helpers={} from {}", packId, loaded, jarPath);
        }
        return List.copyOf(loaded);
    }

    private Path packsRoot() {
        return Path.of(packProperties.getPacksDir()).toAbsolutePath().normalize();
    }

    private static void unzip(byte[] zipBytes, Path targetDir) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path out = targetDir.resolve(entry.getName()).normalize();
                if (!out.startsWith(targetDir)) {
                    throw new IOException("Zip entry escapes target directory: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                Files.createDirectories(out.getParent());
                Files.copy(zip, out, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void copyTree(Path source, Path target) throws IOException {
        try (var walk = Files.walk(source)) {
            for (Path path : walk.toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            for (Path path : walk.sorted((a, b) -> b.compareTo(a)).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ex) {
            log.debug("Failed to delete temp analytics pack dir {}: {}", root, ex.getMessage());
        }
    }
}
