package com.ispf.server.driver.pack;

import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

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
