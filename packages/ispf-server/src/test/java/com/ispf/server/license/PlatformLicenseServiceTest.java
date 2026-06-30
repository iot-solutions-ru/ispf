package com.ispf.server.license;

import com.ispf.server.config.CommercialLicenseProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformLicenseServiceTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CommercialLicenseProperties properties;
    private InstallationIdService installationIdService;
    private PlatformLicenseService service;

    @BeforeEach
    void setUp() throws Exception {
        properties = new CommercialLicenseProperties();
        properties.setDataDir(tempDir.toString());
        properties.setEnforce(true);
        installationIdService = new InstallationIdService(properties);
        installationIdService.ensureInstallationId();
        service = new PlatformLicenseService(
                properties,
                installationIdService,
                objectMapper,
                Optional.empty()
        );
    }

    @Test
    void communityModeDoesNotBlockWhenEnforceEnabled() {
        assertDoesNotThrowEnforce();
        assertTrue(service.currentStatus().valid());
    }

    @Test
    void invalidPlatformLicenseBlocksStartupWhenEnforceEnabled() throws Exception {
        Files.writeString(
                tempDir.resolve("platform-license.json"),
                objectMapper.writeValueAsString(Map.of("tier", "enterprise"))
        );

        assertFalse(service.currentStatus().valid());
        assertThrows(IllegalStateException.class, service::enforceOnStartup);
    }

    @Test
    void validPlatformLicensePassesEnforcement() throws Exception {
        var keyPair = LicenseTestSupport.generateRsaKeyPair();
        properties.setPublicKeyPem(LicenseTestSupport.toPemPublicKey(keyPair));
        Map<String, Object> license = LicenseTestSupport.signedPlatformLicense(
                "enterprise",
                installationIdService.currentInstallationId(),
                keyPair
        );
        Files.writeString(tempDir.resolve("platform-license.json"), objectMapper.writeValueAsString(license));
        service = new PlatformLicenseService(
                properties,
                installationIdService,
                objectMapper,
                Optional.empty()
        );

        assertTrue(service.currentStatus().valid());
        assertDoesNotThrowEnforce();
    }

    private void assertDoesNotThrowEnforce() {
        service.enforceOnStartup();
    }
}
