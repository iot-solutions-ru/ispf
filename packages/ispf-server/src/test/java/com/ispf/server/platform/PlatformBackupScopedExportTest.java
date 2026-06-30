package com.ispf.server.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@SpringBootTest
@ActiveProfiles("test")
class PlatformBackupScopedExportTest {

    @Autowired
    private PlatformBackupService platformBackupService;

    @Test
    void exportsScopedSubtreeUnderRootPlatform() {
        var payload = platformBackupService.exportSubtree("root.platform.devices");
        assertEquals("root.platform.devices", payload.get("rootPath"));
        assertTrue(((Number) payload.get("nodeCount")).intValue() >= 1);
    }

    @Test
    void rejectsExportOutsideRootPlatform() {
        var error = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> platformBackupService.exportSubtree("root.other")
        );
        assertEquals(BAD_REQUEST, error.getStatusCode());
    }
}
