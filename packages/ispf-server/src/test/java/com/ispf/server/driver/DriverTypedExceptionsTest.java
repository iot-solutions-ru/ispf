package com.ispf.server.driver;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Code-analysis F-05: the top-20 industrial drivers must declare <em>why</em> a call failed through the typed
 * {@code DriverException} subclasses so {@code DriverErrorMetrics} / retry policy can act on
 * {@code DriverErrorKind} without parsing messages. A plain {@code throw new DriverException(...)} in the main
 * sources of those packs is a regression.
 */
class DriverTypedExceptionsTest {

    private static final Pattern PLAIN_THROW = Pattern.compile("throw\\s+new\\s+DriverException\\s*\\(");

    @Test
    void top20DriversThrowOnlyTypedDriverExceptions() throws IOException {
        Path repoRoot = repoRoot();
        assertNotNull(repoRoot, "repository root not found (packages/ispf-driver-api missing)");

        List<String> offenders = new ArrayList<>();
        for (String driverId : DriverProductionMatrix.TOP_20_INDUSTRIAL) {
            DriverProductionMatrix.Entry entry = DriverProductionMatrix.entries().get(driverId);
            assertNotNull(entry, "matrix entry missing for top-20 driver " + driverId);
            Path mainJava = repoRoot.resolve("packages").resolve(entry.interopGradleModule())
                    .resolve("src").resolve("main").resolve("java");
            if (!Files.isDirectory(mainJava)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(mainJava)) {
                for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                    String source = Files.readString(file, StandardCharsets.UTF_8);
                    Matcher matcher = PLAIN_THROW.matcher(source);
                    while (matcher.find()) {
                        int line = 1 + (int) source.substring(0, matcher.start()).chars().filter(c -> c == '\n').count();
                        offenders.add(repoRoot.relativize(file).toString().replace('\\', '/') + ":" + line);
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(), () -> "Top-20 drivers must throw DriverTransient/Permanent/Configuration/"
                + "UnsupportedOperationException, not plain DriverException:\n  " + String.join("\n  ", offenders));
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath();
        for (int i = 0; i < 5 && current != null; i++) {
            if (Files.isDirectory(current.resolve("packages").resolve("ispf-driver-api"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }
}
