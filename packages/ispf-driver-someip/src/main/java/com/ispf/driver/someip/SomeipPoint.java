package com.ispf.driver.someip;

import com.ispf.driver.DriverException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed SOME/IP point — {@code service:method} (hex), optional {@code event} synonym for method.
 */
record SomeipPoint(int service, int method) {

    private static final Pattern SERVICE_METHOD = Pattern.compile(
            "^(?:0x)?([0-9A-Fa-f]+)\\s*[:.]\\s*(?:0x)?([0-9A-Fa-f]+)$",
            Pattern.CASE_INSENSITIVE);

    static SomeipPoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("Blank SOME/IP mapping");
        }
        String text = mapping.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith("service:")) {
            text = text.substring("service:".length()).trim();
        }
        Matcher matcher = SERVICE_METHOD.matcher(text);
        if (!matcher.matches()) {
            throw new DriverException(
                    "Unsupported SOME/IP mapping (expected 0x1234:0x0001): " + mapping);
        }
        int service = Integer.parseInt(matcher.group(1), 16);
        int method = Integer.parseInt(matcher.group(2), 16);
        if (service < 0 || service > 0xFFFF || method < 0 || method > 0xFFFF) {
            throw new DriverException("SOME/IP service/method out of range: " + mapping);
        }
        return new SomeipPoint(service, method);
    }

    String formatService() {
        return String.format(Locale.ROOT, "0x%04X", service);
    }

    String formatMethod() {
        return String.format(Locale.ROOT, "0x%04X", method);
    }
}
