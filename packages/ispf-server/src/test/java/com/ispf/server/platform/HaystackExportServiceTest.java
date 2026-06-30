package com.ispf.server.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HaystackExportServiceTest {

    @Test
    void acceptsPathsUnderRoot() {
        assertTrue(HaystackExportService.isUnderRoot(
                "root.platform.devices.lab-userA-01",
                "root.platform.devices"
        ));
        assertTrue(HaystackExportService.isUnderRoot(
                "root.platform.devices",
                "root.platform.devices"
        ));
        assertFalse(HaystackExportService.isUnderRoot(
                "root.platform.reports.demo",
                "root.platform.devices"
        ));
    }

    @Test
    void normalizesBlankRootToPlatformDefault() {
        assertTrue(HaystackExportService.normalizeRootPath(null)
                .startsWith("root.platform"));
        assertTrue(HaystackExportService.normalizeRootPath("  ")
                .startsWith("root.platform"));
    }
}
