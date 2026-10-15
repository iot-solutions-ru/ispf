package com.ispf.server.platform.analytics.pack;

import com.ispf.server.config.AnalyticsPackProperties;
import com.ispf.server.config.CommercialLicenseProperties;
import com.ispf.server.driver.pack.DriverPackLicenseVerifier;
import com.ispf.server.license.InstallationIdService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DropInAnalyticsPackLoaderTest {

    private static Path builtPackDir;

    @BeforeAll
    static void locateBuiltPack() {
        Path candidate = Path.of("..", "..", "ispf-analytics-marketplace-demo", "build", "analytics-packs", "ispf-analytics-kpi-demo")
                .normalize();
        if (!Files.isRegularFile(candidate.resolve("analytics-pack.json"))) {
            candidate = Path.of("packages", "ispf-analytics-marketplace-demo", "build", "analytics-packs", "ispf-analytics-kpi-demo")
                    .normalize();
        }
        builtPackDir = candidate;
    }

    @TempDir
    Path tempDir;

    @Test
    void loadsPackFromZipArchive() throws Exception {
        assumeTrue(Files.isRegularFile(builtPackDir.resolve("analytics-pack.json")),
                "Run :packages:ispf-analytics-marketplace-demo:assembleAnalyticsPackDir first");

        AnalyticsExtensionRegistry registry = new AnalyticsExtensionRegistry();
        AnalyticsPackLoader classpathLoader = new AnalyticsPackLoader(registry);
        AnalyticsPackProperties properties = new AnalyticsPackProperties();
        properties.setPacksDir(tempDir.resolve("packs").toString());
        CommercialLicenseProperties licenseProperties = new CommercialLicenseProperties();
        licenseProperties.setEnforce(false);
        InstallationIdService installationIdService = new InstallationIdService(licenseProperties);
        DriverPackLicenseVerifier licenseVerifier = new DriverPackLicenseVerifier(
                licenseProperties,
                installationIdService,
                java.util.Optional.empty()
        );
        AnalyticsPackLicenseSigner licenseSigner = new AnalyticsPackLicenseSigner(
                licenseProperties,
                installationIdService,
                java.util.Optional.empty(),
                licenseVerifier
        );
        DropInAnalyticsPackLoader loader = new DropInAnalyticsPackLoader(
                properties,
                classpathLoader,
                new ObjectMapper(),
                licenseProperties,
                licenseSigner
        );

        Path zipPath = tempDir.resolve("demo.zip");
        try (var zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(zipPath))) {
            try (var walk = Files.walk(builtPackDir)) {
                for (Path path : walk.filter(Files::isRegularFile).toList()) {
                    String entryName = builtPackDir.relativize(path).toString().replace('\\', '/');
                    zos.putNextEntry(new java.util.zip.ZipEntry(entryName));
                    Files.copy(path, zos);
                    zos.closeEntry();
                }
            }
        }

        var helpers = loader.installZipArchive(Files.readAllBytes(zipPath), "ispf-analytics-kpi-demo");

        assertThat(helpers).contains("percentChange");
        assertThat(registry.containsHelper("percentChange")).isTrue();
        assertThat(loader.isPackInstalled("ispf-analytics-kpi-demo")).isTrue();
    }

    @Test
    void rejectsPackIdEscapingPacksRoot() throws Exception {
        DropInAnalyticsPackLoader loader = newLoader(tempDir.resolve("packs"));

        Path sourceDir = Files.createDirectories(tempDir.resolve("source"));
        Files.writeString(
                sourceDir.resolve("analytics-pack.json"),
                "{\"packId\":\"../evil\",\"jarFile\":\"evil.jar\"}"
        );
        Files.writeString(sourceDir.resolve("evil.jar"), "not-a-jar");

        assertThatThrownBy(() -> loader.installPackDirectory(sourceDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid pack id path");
        assertThat(tempDir.resolve("evil")).doesNotExist();
    }

    @Test
    void rejectsZipEntryEscapingTargetDirectory() throws Exception {
        DropInAnalyticsPackLoader loader = newLoader(tempDir.resolve("packs"));

        Path zipPath = tempDir.resolve("evil.zip");
        try (var zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.putNextEntry(new java.util.zip.ZipEntry("../evil.txt"));
            zos.write("evil".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        assertThatThrownBy(() -> loader.installZipArchive(Files.readAllBytes(zipPath), null))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("escapes target directory");
    }

    @Test
    void catalogNamesInvalidManifestAndMissingJar() throws Exception {
        Path packsDir = tempDir.resolve("packs");
        Path broken = Files.createDirectories(packsDir.resolve("broken-manifest"));
        Files.writeString(broken.resolve("analytics-pack.json"), "{not-json");
        Path missingJar = Files.createDirectories(packsDir.resolve("missing-jar"));
        Files.writeString(
                missingJar.resolve("analytics-pack.json"),
                "{\"packId\":\"missing-jar\",\"jarFile\":\"absent.jar\",\"version\":\"1\"}"
        );
        DropInAnalyticsPackLoader loader = newLoader(packsDir);

        loader.loadDropInPacksOnStartup();
        List<Map<String, Object>> packs = loader.listInstalledPacks();

        assertThat(packs).extracting(row -> row.get("packId"))
                .containsExactly("broken-manifest", "missing-jar");
        assertThat(packs).allSatisfy(row -> {
            assertThat(row.get("status")).isEqualTo("failed");
            assertThat(row.get("error").toString()).isNotBlank();
        });
        assertThat(packs.get(1).get("error").toString()).contains("JAR not found").contains("absent.jar");
    }

    private static DropInAnalyticsPackLoader newLoader(Path packsDir) {
        AnalyticsExtensionRegistry registry = new AnalyticsExtensionRegistry();
        AnalyticsPackLoader classpathLoader = new AnalyticsPackLoader(registry);
        AnalyticsPackProperties properties = new AnalyticsPackProperties();
        properties.setPacksDir(packsDir.toString());
        CommercialLicenseProperties licenseProperties = new CommercialLicenseProperties();
        licenseProperties.setEnforce(false);
        InstallationIdService installationIdService = new InstallationIdService(licenseProperties);
        DriverPackLicenseVerifier licenseVerifier = new DriverPackLicenseVerifier(
                licenseProperties,
                installationIdService,
                java.util.Optional.empty()
        );
        AnalyticsPackLicenseSigner licenseSigner = new AnalyticsPackLicenseSigner(
                licenseProperties,
                installationIdService,
                java.util.Optional.empty(),
                licenseVerifier
        );
        return new DropInAnalyticsPackLoader(
                properties,
                classpathLoader,
                new ObjectMapper(),
                licenseProperties,
                licenseSigner
        );
    }
}
