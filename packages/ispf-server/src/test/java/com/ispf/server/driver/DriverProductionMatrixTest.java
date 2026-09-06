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

import static com.ispf.server.driver.DriverProductionMatrix.Capability.POLL;
import static com.ispf.server.driver.DriverProductionMatrix.Capability.WRITE;
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

    /** ADR-0057 / Wave 1: known write-capable packs must not under-claim WRITE. */
    @Test
    void wave1WriteCapableDriversDeclareWrite() {
        for (String driverId : new String[] {
            "modbus-tcp", "mqtt", "opcua", "http", "snmp", "ethernet-ip",
            "smpp", "email", "sms", "webhook", "bacnet"
        }) {
            assertTrue(
                    DriverProductionMatrix.resolveCapabilities(driverId).contains(WRITE),
                    driverId + " must declare WRITE (ADR-0057 honesty)"
            );
        }
        assertTrue(DriverProductionMatrix.resolveCapabilities("dnp3").contains(POLL));
        assertFalse(
                DriverProductionMatrix.resolveCapabilities("dnp3").contains(WRITE),
                "dnp3 stays POLL_ONLY until write is implemented"
        );
        assertEquals(DriverMaturity.PRODUCTION, DriverProductionMatrix.resolveMaturity("dnp3"));
        assertEquals(DriverMaturity.BETA, DriverProductionMatrix.resolveMaturity("opc-da"));
        assertEquals(DriverMaturity.BETA, DriverProductionMatrix.resolveMaturity("opc-bridge"));
    }

    /**
     * Wave 1 honesty: curated WRITE drivers must not ship a stub {@code writePoint};
     * curated POLL_ONLY drivers must keep an explicit not-implemented write.
     */
    @Test
    void wave1WritePointSourceMatchesDeclaredCapabilities() throws IOException {
        assertWritePointMentionsNotImplemented("dnp3", true);
        assertWritePointMentionsNotImplemented("gps-tracker", true);
        assertWritePointMentionsNotImplemented("modbus-tcp", false);
        assertWritePointMentionsNotImplemented("http", false);
        assertWritePointMentionsNotImplemented("ethernet-ip", false);
        assertWritePointMentionsNotImplemented("snmp", false);
    }

    private static void assertWritePointMentionsNotImplemented(String driverId, boolean expectStub)
            throws IOException {
        DriverProductionMatrix.Entry entry = DriverProductionMatrix.entry(driverId).orElseThrow();
        Path source = resolveDeviceDriverSource(entry);
        assertNotNull(source, driverId + " DeviceDriver source");
        String text = Files.readString(source);
        int idx = text.indexOf("void writePoint");
        assertTrue(idx >= 0, driverId + " missing writePoint method");
        String window = text.substring(idx, Math.min(text.length(), idx + 700));
        boolean looksStub = Pattern.compile(
                        "not implemented|unsupported operation|read-only",
                        Pattern.CASE_INSENSITIVE
                )
                .matcher(window)
                .find();
        if (expectStub) {
            assertTrue(looksStub, driverId + " POLL_ONLY writePoint should document not-implemented");
            assertFalse(entry.capabilities().contains(WRITE), driverId + " must not claim WRITE");
        } else {
            assertFalse(looksStub, driverId + " WRITE driver writePoint must not be a stub throw");
            assertTrue(entry.capabilities().contains(WRITE), driverId + " must claim WRITE");
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
