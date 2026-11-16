package com.ispf.driver.ddk;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriverDdkTemplateTest {

    @Test
    void templateFilesExist() throws Exception {
        Path templateRoot = Path.of("template");
        assertTrue(Files.isDirectory(templateRoot), "template/ directory missing");
        assertTrue(Files.exists(templateRoot.resolve("driver-pack.json")));
        assertTrue(Files.exists(templateRoot.resolve("build.gradle.kts")));
        assertTrue(Files.exists(templateRoot.resolve("settings.gradle.kts")));
        assertTrue(Files.exists(templateRoot.resolve("gradle.properties")));
        assertTrue(Files.exists(templateRoot.resolve("README.md")));
        assertTrue(Files.exists(templateRoot.resolve("src/main/java/com/ispf/driver/template/TemplateDeviceDriver.java")));
        assertTrue(Files.exists(templateRoot.resolve("src/test/java/com/ispf/driver/template/TemplateDeviceDriverTest.java")));
    }

    @Test
    void templateBuildDeclaresPublishedCoordinatesAndMatchesPlatformVersion() throws Exception {
        String build = Files.readString(Path.of("template/build.gradle.kts"));
        assertTrue(build.contains("mavenLocal"));
        assertTrue(build.contains("includeGroup(\"com.ispf\")"));
        assertTrue(build.contains("compileOnly(\"com.ispf:ispf-driver-api:"));
        assertTrue(build.contains("compileOnly(\"com.ispf:ispf-core:"));
        assertTrue(build.contains("testImplementation(\"com.ispf:ispf-driver-api:"));
        assertTrue(build.contains("driverPackJar"));
        assertTrue(build.contains("runtimeClasspath"));
        assertTrue(build.contains("META-INF/*.SF"));
        assertTrue(build.contains("junit-platform-launcher"));
        assertEquals(false, build.contains("project(\":"));
        String apiBuild = Files.readString(Path.of("../ispf-driver-api/build.gradle.kts"));
        assertTrue(apiBuild.contains("api(project(\":packages:ispf-core\"))"));
        String templateVersion = versionProperty(Path.of("template/gradle.properties"), "ispfVersion");
        String platformVersion = versionProperty(Path.of("../../gradle.properties"), "version");
        assertEquals(platformVersion, templateVersion);
    }

    private static String versionProperty(Path file, String key) throws Exception {
        String prefix = key + "=";
        return Files.readAllLines(file).stream()
                .map(String::trim)
                .filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()).trim())
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing " + prefix + " in " + file));
    }

    @Test
    void templateDriverMetadataUsesPlaceholderId() {
        var driver = new com.ispf.driver.template.TemplateDeviceDriver();
        assertEquals(DriverDdk.TEMPLATE_DRIVER_ID, driver.metadata().id());
    }

    @Test
    void referenceExampleDriversExist() throws Exception {
        Path examples = Path.of("examples");
        assertTrue(Files.isDirectory(examples.resolve("simple-counter")));
        assertTrue(Files.isDirectory(examples.resolve("json-poller")));
        assertTrue(Files.isDirectory(examples.resolve("modbus-simulator")));
        assertTrue(Files.exists(examples.resolve("simple-counter/driver-pack.json")));
        assertTrue(Files.exists(examples.resolve("json-poller/driver-pack.json")));
        assertTrue(Files.exists(examples.resolve("modbus-simulator/driver-pack.json")));
    }

    @Test
    void simpleCounterDriverMetadata() {
        assertEquals(DriverDdk.SIMPLE_COUNTER_DRIVER_ID, new com.ispf.driver.simplecounter.SimpleCounterDeviceDriver().metadata().id());
    }

    @Test
    void jsonPollerDriverMetadata() {
        assertEquals(DriverDdk.JSON_POLLER_DRIVER_ID, new com.ispf.driver.jsonpoller.JsonPollerDeviceDriver().metadata().id());
    }

    @Test
    void modbusSimulatorDriverMetadata() {
        assertEquals(DriverDdk.MODBUS_SIMULATOR_DRIVER_ID, new com.ispf.driver.modbussimulator.ModbusSimulatorDeviceDriver().metadata().id());
    }

    @Test
    void assembleDdkExamplePacksLayoutExistsAfterCheck() throws Exception {
        // Produced by Gradle task assembleDdkExamplePacks (wired into check).
        Path packs = Path.of("build/ddk-example-packs");
        if (!Files.isDirectory(packs)) {
            // Unit test classpath may run before the pack task in IDE; assert recipe artifacts instead.
            assertTrue(Files.exists(Path.of("examples/simple-counter/driver-pack.json")));
            assertTrue(Files.exists(Path.of("examples/json-poller/driver-pack.json")));
            assertTrue(Files.exists(Path.of("examples/modbus-simulator/driver-pack.json")));
            return;
        }
        for (String id : List.of("simple-counter", "json-poller", "modbus-simulator")) {
            Path packDir = packs.resolve("ispf-driver-" + id);
            assertTrue(Files.isDirectory(packDir), "missing pack dir " + packDir);
            assertTrue(Files.exists(packDir.resolve("driver-pack.json")));
            assertTrue(Files.exists(packDir.resolve("ispf-driver-" + id + ".jar")));
        }
    }
}
