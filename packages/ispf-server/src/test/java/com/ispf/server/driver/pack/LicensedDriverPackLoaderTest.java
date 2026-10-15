package com.ispf.server.driver.pack;

import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LicensedDriverPackLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void skipsPackWithoutLicenseWhenEnforceFalse() throws Exception {
        Path packDir = tempDir.resolve("demo-pack");
        Files.createDirectories(packDir);
        Path jarPath = packDir.resolve("demo.jar");
        Files.writeString(jarPath, "placeholder");

        Map<String, Object> manifest = Map.of(
                "packId", "demo-pack",
                "minPlatformVersion", "0.7.5",
                "jarFile", "demo.jar",
                "drivers", java.util.List.of(Map.of(
                        "driverId", "demo-licensed",
                        "driverClass", LicensedDriverPackLoaderTest.class.getName()
                ))
        );
        new ObjectMapper().writeValue(packDir.resolve("driver-pack.json").toFile(), manifest);

        LicensedDriverRegistry registry = new LicensedDriverRegistry();
        LicensedDriverPackLoader loader = new LicensedDriverPackLoader(
                packProperties(packDir.getParent()),
                licenseProperties(false),
                null,
                registry,
                new ObjectMapper()
        );

        loader.loadPackDirectory(packDir);
        assertTrue(registry.metadata().isEmpty());
        assertEquals(1, registry.packLoadFailures().size());
        LicensedDriverRegistry.PackLoadFailure failure = registry.packLoadFailures().get(0);
        assertEquals("demo-pack", failure.packId());
        assertTrue(failure.reason().startsWith("load failed:"));
    }

    @Test
    void recordsFailureForInvalidManifest() throws Exception {
        Path packDir = tempDir.resolve("broken-pack");
        Files.createDirectories(packDir);
        Map<String, Object> manifest = Map.of(
                "packId", " ",
                "jarFile", "demo.jar"
        );
        new ObjectMapper().writeValue(packDir.resolve("driver-pack.json").toFile(), manifest);

        LicensedDriverRegistry registry = new LicensedDriverRegistry();
        LicensedDriverPackLoader loader = new LicensedDriverPackLoader(
                packProperties(packDir.getParent()),
                licenseProperties(false),
                null,
                registry,
                new ObjectMapper()
        );

        loader.loadPackDirectory(packDir);
        assertEquals(1, registry.packLoadFailures().size());
        LicensedDriverRegistry.PackLoadFailure failure = registry.packLoadFailures().get(0);
        assertNull(failure.packId());
        assertEquals("invalid driver pack manifest", failure.reason());
    }

    @Test
    void recordsFailureForMissingJar() throws Exception {
        Path packDir = tempDir.resolve("no-jar-pack");
        Files.createDirectories(packDir);
        Map<String, Object> manifest = Map.of(
                "packId", "no-jar-pack",
                "jarFile", "missing.jar"
        );
        new ObjectMapper().writeValue(packDir.resolve("driver-pack.json").toFile(), manifest);

        LicensedDriverRegistry registry = new LicensedDriverRegistry();
        LicensedDriverPackLoader loader = new LicensedDriverPackLoader(
                packProperties(packDir.getParent()),
                licenseProperties(false),
                null,
                registry,
                new ObjectMapper()
        );

        loader.loadPackDirectory(packDir);
        assertEquals(1, registry.packLoadFailures().size());
        LicensedDriverRegistry.PackLoadFailure failure = registry.packLoadFailures().get(0);
        assertEquals("no-jar-pack", failure.packId());
        assertTrue(failure.reason().startsWith("JAR not found:"));
    }

    @Test
    void recordsFailureWhenLicenseBlockMissingUnderEnforce() throws Exception {
        Path packDir = tempDir.resolve("unlicensed-pack");
        Files.createDirectories(packDir);
        Files.writeString(packDir.resolve("demo.jar"), "placeholder");
        Map<String, Object> manifest = Map.of(
                "packId", "unlicensed-pack",
                "jarFile", "demo.jar"
        );
        new ObjectMapper().writeValue(packDir.resolve("driver-pack.json").toFile(), manifest);

        LicensedDriverRegistry registry = new LicensedDriverRegistry();
        LicensedDriverPackLoader loader = new LicensedDriverPackLoader(
                packProperties(packDir.getParent()),
                licenseProperties(true),
                null,
                registry,
                new ObjectMapper()
        );

        loader.loadPackDirectory(packDir);
        assertTrue(registry.metadata().isEmpty());
        assertEquals(1, registry.packLoadFailures().size());
        LicensedDriverRegistry.PackLoadFailure failure = registry.packLoadFailures().get(0);
        assertEquals("unlicensed-pack", failure.packId());
        assertEquals("license block required when enforce=true", failure.reason());
    }

    private static com.ispf.server.config.DriverPackProperties packProperties(Path root) {
        com.ispf.server.config.DriverPackProperties properties = new com.ispf.server.config.DriverPackProperties();
        properties.setPacksDir(root.toString());
        return properties;
    }

    private static com.ispf.server.config.CommercialLicenseProperties licenseProperties(boolean enforce) {
        com.ispf.server.config.CommercialLicenseProperties properties =
                new com.ispf.server.config.CommercialLicenseProperties();
        properties.setEnforce(enforce);
        return properties;
    }

    /** Not a real driver — loader should fail class load before register. */
    public static class NotADriver {
        public DriverMetadata metadata() {
            return null;
        }
    }
}
