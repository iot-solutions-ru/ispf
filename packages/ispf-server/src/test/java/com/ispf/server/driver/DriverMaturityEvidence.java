package com.ispf.server.driver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Mechanical evidence behind a {@code PRODUCTION} entry in {@link DriverProductionMatrix} (code-analysis
 * F-03, ADR-0022 honesty). A pack may only claim PRODUCTION when all of the following hold:
 * <ol>
 *   <li>the driver module carries at least {@link #MIN_MAIN_LOC} non-comment lines of main code — a
 *       {@code DeviceDriver} with connect/read/write/point-parsing cannot be smaller than that;</li>
 *   <li>the module has at least {@link #MIN_TESTS} {@code @Test} methods and at least one test class
 *       whose name says it exercises the driver (not only the point parser);</li>
 *   <li>something answers on the other side of the wire: an interop-lab fixture
 *       ({@code deploy/driver-interop/docker-compose.yml}), an in-process emulated peer in the module
 *       tests (socket server, embedded broker, fake gateway), or the driver is transport-less
 *       ({@link #TRANSPORT_LESS}: filesystem / in-JVM / in-memory database).</li>
 * </ol>
 * Everything else is BETA. The verdict is deterministic and computed from the working tree, so the
 * matrix cannot drift from what the repository can actually prove.
 */
final class DriverMaturityEvidence {

    static final int MIN_MAIN_LOC = 100;
    static final int MIN_TESTS = 2;

    /** Drivers whose "device" is the local host or an in-JVM backend; their tests hit the real thing. */
    static final Set<String> TRANSPORT_LESS = Set.of(
            "virtual",      // in-memory simulated device
            "file",         // local filesystem
            "folder",       // local filesystem
            "application",  // local process / JVM
            "flexible",     // scripting driver: checksum / extractor / template tests are the codec
            "jdbc",         // H2 in-memory through the real JDBC path
            "odbc"          // same JDBC wrapper; the ODBC bridge JAR is operator-supplied
    );

    /** Compose service name differs from the driver id. */
    private static final Map<String, String> INTEROP_SERVICE_ALIAS = Map.of("mqtt", "mosquitto");

    /** Test-side markers of an emulated peer: something is listening and the driver talks to it. */
    static final Pattern PEER_MARKERS = Pattern.compile(
            "ServerSocket|DatagramSocket|HttpServer|ServerSocketChannel|DatagramChannel|EmbeddedKafka"
                    + "|Loopback|Fake\\w*(Gateway|Server|Bridge|Peer|Bmc|Device|Port|Transport)"
                    + "|Mock\\w*(Server|Bmc|Peer|Broker)|Testcontainers|GenericContainer"
                    + "|\\.bind\\(|\\.listen\\(|Server\\(|Broker\\(|Simulat|Emulat"
                    + "|Stub\\w*(Port|Transport|Server|Gateway)"
    );

    private static final Pattern COMPOSE_SERVICE = Pattern.compile("(?m)^  ([a-z0-9-]+):\\s*$");
    private static final Pattern TEST_ANNOTATION = Pattern.compile("@Test\\b");

    record Verdict(String driverId, int mainLoc, int tests, boolean driverTestClass, String peerEvidence,
                   List<String> failures) {
        boolean production() {
            return failures.isEmpty();
        }
    }

    private DriverMaturityEvidence() {
    }

    static Verdict evaluate(DriverProductionMatrix.Entry entry, Path repoRoot) {
        String module = entry.interopGradleModule();
        List<String> failures = new ArrayList<>();
        if (module == null || module.isBlank()) {
            failures.add("no gradle module declared");
            return new Verdict(entry.driverId(), 0, 0, false, "none", failures);
        }
        Path moduleDir = repoRoot.resolve("packages").resolve(module);
        int mainLoc = javaFiles(moduleDir.resolve("src/main/java")).stream().mapToInt(DriverMaturityEvidence::loc).sum();
        List<Path> testFiles = javaFiles(moduleDir.resolve("src/test/java"));
        int tests = 0;
        boolean peerInTests = false;
        boolean driverTestClass = false;
        for (Path testFile : testFiles) {
            String source = read(testFile);
            tests += (int) TEST_ANNOTATION.matcher(source).results().count();
            peerInTests |= PEER_MARKERS.matcher(source).find();
            driverTestClass |= testFile.getFileName().toString().contains("Driver");
        }

        String peerEvidence;
        if (hasInteropFixture(entry.driverId(), repoRoot)) {
            peerEvidence = "interop-lab fixture";
        } else if (TRANSPORT_LESS.contains(entry.driverId())) {
            peerEvidence = "transport-less";
        } else if (peerInTests) {
            peerEvidence = "emulated peer in tests";
        } else {
            peerEvidence = "none";
        }

        if (mainLoc < MIN_MAIN_LOC) {
            failures.add("main code " + mainLoc + " LOC < " + MIN_MAIN_LOC);
        }
        if (tests < MIN_TESTS) {
            failures.add("only " + tests + " @Test method(s) < " + MIN_TESTS);
        }
        if (!driverTestClass) {
            failures.add("no *Driver*Test class (only parser/codec tests)");
        }
        if ("none".equals(peerEvidence)) {
            failures.add("no device on the other side: no interop fixture, no emulated peer in tests, not transport-less");
        }
        return new Verdict(entry.driverId(), mainLoc, tests, driverTestClass, peerEvidence, List.copyOf(failures));
    }

    static boolean hasInteropFixture(String driverId, Path repoRoot) {
        Path compose = repoRoot.resolve("deploy/driver-interop/docker-compose.yml");
        if (!Files.isRegularFile(compose)) {
            return false;
        }
        String service = INTEROP_SERVICE_ALIAS.getOrDefault(driverId, driverId);
        return COMPOSE_SERVICE.matcher(read(compose)).results()
                .anyMatch(m -> m.group(1).equals(service));
    }

    /** Non-blank lines that are not comments, imports or the package declaration. */
    static int loc(Path javaFile) {
        int count = 0;
        boolean inBlockComment = false;
        for (String raw : read(javaFile).split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (inBlockComment) {
                if (line.contains("*/")) {
                    inBlockComment = false;
                }
                continue;
            }
            if (line.startsWith("/*")) {
                if (!line.contains("*/")) {
                    inBlockComment = true;
                }
                continue;
            }
            if (line.startsWith("//") || line.startsWith("*")
                    || line.startsWith("import ") || line.startsWith("package ")) {
                continue;
            }
            count++;
        }
        return count;
    }

    private static List<Path> javaFiles(Path root) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".java"))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
