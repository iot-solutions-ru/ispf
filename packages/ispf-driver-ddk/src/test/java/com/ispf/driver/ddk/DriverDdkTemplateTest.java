package com.ispf.driver.ddk;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriverDdkTemplateTest {

    @Test
    void templateFilesExist() throws Exception {
        Path templateRoot = Path.of("template");
        assertTrue(Files.isDirectory(templateRoot), "template/ directory missing");
        assertTrue(Files.exists(templateRoot.resolve("driver-pack.json")));
        assertTrue(Files.exists(templateRoot.resolve("build.gradle.kts")));
        assertTrue(Files.exists(templateRoot.resolve("README.md")));
        assertTrue(Files.exists(templateRoot.resolve("src/main/java/com/ispf/driver/template/TemplateDeviceDriver.java")));
        assertTrue(Files.exists(templateRoot.resolve("src/test/java/com/ispf/driver/template/TemplateDeviceDriverTest.java")));
    }

    @Test
    void templateDriverMetadataUsesPlaceholderId() {
        var driver = new com.ispf.driver.template.TemplateDeviceDriver();
        assertEquals(DriverDdk.TEMPLATE_DRIVER_ID, driver.metadata().id());
    }
}
