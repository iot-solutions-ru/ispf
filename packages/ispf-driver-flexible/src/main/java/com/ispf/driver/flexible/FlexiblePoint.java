package com.ispf.driver.flexible;

import java.util.regex.Pattern;

/**
 * Point mapping: {@code request} or {@code request:responseRegex}.
 * Sends request bytes/string, reads response; optional regex capture group 1 becomes value.
 */
public record FlexiblePoint(String request, Pattern responseRegex) {

    public static FlexiblePoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Flexible point mapping is blank");
        }
        String trimmed = raw.trim();
        int colon = trimmed.indexOf(':');
        if (colon > 0) {
            String request = trimmed.substring(0, colon);
            String regex = trimmed.substring(colon + 1);
            if (request.isBlank()) {
                throw new IllegalArgumentException("Flexible request is blank");
            }
            if (regex.isBlank()) {
                return new FlexiblePoint(request, null);
            }
            return new FlexiblePoint(request, Pattern.compile(regex, Pattern.DOTALL));
        }
        return new FlexiblePoint(trimmed, null);
    }
}
