package com.ispf.server.driver;

import com.ispf.driver.DriverMaturity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class DriverProductionMatrixTest {

    /** Top-20 partners / honesty-downgraded shells (BL-191) — not required PRODUCTION. */
    private static final Set<String> TOP_20_NON_PRODUCTION_EXEMPT = Set.of(
            "opc-da",
            "opc-bridge"
    );

    private static final Pattern CLASS_JAVADOC = Pattern.compile(
            "(?s)/\\*\\*(.*?)\\*/\\s*(?:@[\\w.]+(?:\\([^)]*\\))?\\s*)*public\\s+class\\s+\\w+"
    );
    private static final Pattern STUB_OR_PLACEHOLDER = Pattern.compile(
            "\\b(stub|placeholder)\\b",
            Pattern.CASE_INSENSITIVE
    );

    @Test
    void top10IndustrialDriversAreProduction() {
        for (String driverId : DriverProductionMatrix.TOP_10_INDUSTRIAL) {
            assertEquals(
                    DriverMaturity.PRODUCTION,
                    DriverProductionMatrix.resolveMaturity(driverId),
                    driverId
            );
            DriverProductionMatrix.Entry entry = DriverProductionMatrix.entry(driverId).orElseThrow();
            assertTrue(
                    DriverProductionMatrix.loopbackTestSourceExists(entry),
                    driverId + " missing loopback test"
            );
        }
    }

    @Test
    void top20IndustrialDriversAreProductionOrInteropPartner() {
        for (String driverId : DriverProductionMatrix.TOP_20_INDUSTRIAL) {
            if (TOP_20_NON_PRODUCTION_EXEMPT.contains(driverId)) {
                continue;
            }
            assertEquals(
                    DriverMaturity.PRODUCTION,
                    DriverProductionMatrix.resolveMaturity(driverId),
                    driverId
            );
            DriverProductionMatrix.Entry entry = DriverProductionMatrix.entry(driverId).orElseThrow();
            assertTrue(
                    DriverProductionMatrix.loopbackTestSourceExists(entry),
                    driverId + " missing loopback test"
            );
        }
    }

    @Test
    void productionDriversDeclareLoopbackTestSource() {
        for (DriverProductionMatrix.Entry entry : DriverProductionMatrix.entries().values()) {
            if (entry.maturity() != DriverMaturity.PRODUCTION) {
                continue;
            }
            assertNotNull(entry.loopbackTestSourcePath(), entry.driverId() + " PRODUCTION requires loopback test");
            assertTrue(
                    DriverProductionMatrix.loopbackTestSourceExists(entry),
                    entry.driverId() + " missing loopback test source: " + entry.loopbackTestSourcePath()
            );
        }
    }

    @Test
    void productionDriversMustNotBeDocumentedStubs() throws IOException {
        for (DriverProductionMatrix.Entry entry : DriverProductionMatrix.entries().values()) {
            if (entry.maturity() != DriverMaturity.PRODUCTION) {
                continue;
            }
            Path driverSource = resolveDeviceDriverSource(entry);
            assertNotNull(
                    driverSource,
                    entry.driverId() + " PRODUCTION requires a DeviceDriver source to scan"
            );
            String source = Files.readString(driverSource);
            Matcher javadocMatcher = CLASS_JAVADOC.matcher(source);
            assertTrue(
                    javadocMatcher.find(),
                    entry.driverId() + " missing class javadoc in " + driverSource
            );
            String classJavadoc = javadocMatcher.group(1);
            assertFalse(
                    STUB_OR_PLACEHOLDER.matcher(classJavadoc).find(),
                    entry.driverId() + " PRODUCTION class javadoc must not document stub/placeholder: "
                            + driverSource
            );
        }
    }

    @Test
    void maturityRegistryMatchesMatrix() {
        for (String driverId : DriverProductionMatrix.entries().keySet()) {
            assertEquals(
                    DriverProductionMatrix.resolveMaturity(driverId),
                    DriverMaturityRegistry.resolve(driverId),
                    driverId
            );
        }
    }

    @Test
    void topProtocolsDeclareObservedAtCapability() {
        for (String driverId : new String[] { "modbus-tcp", "opcua", "bacnet", "s7", "snmp" }) {
            assertTrue(
                    DriverProductionMatrix.resolveCapabilities(driverId).contains(DriverProductionMatrix.Capability.OBSERVED_AT),
                    driverId
            );
        }
    }

    @Test
    void top10IndustrialDriversLinkedToInteropMatrix() {
        for (String driverId : DriverProductionMatrix.TOP_10_INDUSTRIAL) {
            assertTrue(
                    DriverProductionMatrix.resolveInteropGradleModule(driverId).isPresent(),
                    driverId
            );
        }
    }

    @Test
    void top20IndustrialDriversLinkedToInteropMatrix() {
        for (String driverId : DriverProductionMatrix.TOP_20_INDUSTRIAL) {
            assertTrue(
                    DriverProductionMatrix.resolveInteropGradleModule(driverId).isPresent(),
                    driverId
            );
        }
    }

    private static Path resolveDeviceDriverSource(DriverProductionMatrix.Entry entry) throws IOException {
        String module = entry.interopGradleModule();
        if (module == null || module.isBlank()) {
            return null;
        }
        Path mainJava = firstExisting(
                Path.of("packages", module, "src", "main", "java"),
                Path.of("..", "..", "packages", module, "src", "main", "java")
        );
        if (mainJava == null || !Files.isDirectory(mainJava)) {
            fail(entry.driverId() + " main java tree missing for module " + module);
        }
        try (Stream<Path> walk = Files.walk(mainJava)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith("DeviceDriver.java"))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static Path firstExisting(Path first, Path second) {
        if (Files.exists(first)) {
            return first;
        }
        if (Files.exists(second)) {
            return second.normalize();
        }
        return null;
    }
}
