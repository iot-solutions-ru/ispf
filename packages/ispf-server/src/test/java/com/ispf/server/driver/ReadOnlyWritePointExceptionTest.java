package com.ispf.server.driver;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A read-only {@code writePoint} is unsupported, not a generic failure.
 * Callers use {@code DriverErrorKind.UNSUPPORTED} to tell "this driver cannot write"
 * from a broken write.
 */
class ReadOnlyWritePointExceptionTest {

    private static final Pattern WRITE_IMPL = Pattern.compile(
            "\\.(writeProperty|writePoint|writeTag|writeRegister|writeCoil|submit|publish|setOid|setParameter|"
                    + "writeValue|sendText|sendBinary|send\\()\\s*\\(",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern THROWN_MESSAGE = Pattern.compile(
            "(?:Driver\\w*Exception|UnsupportedOperationException)\\(\\s*\"([^\"]*)\""
    );
    private static final Pattern STUB_MESSAGE = Pattern.compile(
            "not implemented|unsupported operation|write not supported|rejects writes|"
                    + "\\bis read-only\\b|\\bread only\\b|read-only driver|driver is read-only|read-only in v\\d|"
                    + "read-only stub|readout-only|get-only|poll-only|writepoint is not supported",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern RAW_DRIVER_EXCEPTION = Pattern.compile("throw new DriverException\\b");

    @Test
    void stubWritePointThrowsUnsupportedOperation() throws IOException {
        Path packages = firstExisting(Path.of("packages"), Path.of("..", "..", "packages"));
        assertTrue(packages != null, "packages/ not found from " + Path.of("").toAbsolutePath());
        List<String> violations = new ArrayList<>();
        int stubs = 0;
        try (Stream<Path> walk = Files.walk(packages)) {
            List<Path> sources = walk
                    .filter(path -> path.toString().replace('\\', '/').contains("/src/main/java/"))
                    .filter(path -> path.getFileName().toString().endsWith("DeviceDriver.java"))
                    .toList();
            for (Path source : sources) {
                String window = writePointWindow(Files.readString(source));
                if (!looksLikeReadOnlyStub(window)) {
                    continue;
                }
                stubs++;
                String name = packages.relativize(source).toString().replace('\\', '/');
                if (RAW_DRIVER_EXCEPTION.matcher(window).find()
                        || !window.contains("throw new DriverUnsupportedOperationException")) {
                    violations.add(name);
                }
            }
        }
        assertTrue(stubs >= 50, "expected the read-only writePoint catalog, found " + stubs);
        assertTrue(violations.isEmpty(), () -> "stub writePoint still throws DriverException:\n"
                + String.join("\n", violations));
    }

    private static boolean looksLikeReadOnlyStub(String window) {
        if (window.isEmpty() || WRITE_IMPL.matcher(window).find()) {
            return false;
        }
        if (window.contains("throw new UnsupportedOperationException")) {
            return true;
        }
        Matcher messages = THROWN_MESSAGE.matcher(window);
        while (messages.find()) {
            if (STUB_MESSAGE.matcher(messages.group(1)).find()) {
                return true;
            }
        }
        return false;
    }

    private static String writePointWindow(String source) {
        int idx = source.indexOf("void writePoint");
        if (idx < 0) {
            return "";
        }
        int brace = source.indexOf('{', idx);
        if (brace < 0) {
            return "";
        }
        int depth = 0;
        int limit = Math.min(source.length(), brace + 4000);
        for (int i = brace; i < limit; i++) {
            char ch = source.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(idx, i + 1);
                }
            }
        }
        return "";
    }

    private static Path firstExisting(Path first, Path second) {
        if (Files.exists(first)) {
            return first;
        }
        if (Files.exists(second)) {
            return second;
        }
        return null;
    }
}
