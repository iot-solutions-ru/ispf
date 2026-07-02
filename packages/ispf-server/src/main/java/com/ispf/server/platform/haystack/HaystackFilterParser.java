package com.ispf.server.platform.haystack;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * BL-101/102: subset Haystack filter syntax — marker conjunction with {@code and}.
 */
public final class HaystackFilterParser {

    private static final Pattern MARKER = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*");

    private HaystackFilterParser() {
    }

    public static List<String> parseRequiredMarkers(String filter) {
        if (filter == null || filter.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "filter is required.");
        }
        String normalized = filter.trim();
        String[] parts = normalized.split("(?i)\\band\\b");
        List<String> markers = new ArrayList<>();
        for (String part : parts) {
            String token = part.trim();
            if (token.isEmpty()) {
                throw invalidFilter(filter);
            }
            if (!MARKER.matcher(token).matches()) {
                throw invalidFilter(filter);
            }
            markers.add(token);
        }
        if (markers.isEmpty()) {
            throw invalidFilter(filter);
        }
        return List.copyOf(markers);
    }

    public static String toFilterString(List<String> markers) {
        if (markers == null || markers.isEmpty()) {
            return "";
        }
        return String.join(" and ", markers);
    }

    private static ResponseStatusException invalidFilter(String filter) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid Haystack filter (v1 supports marker conjunction with 'and', e.g. \"point and temp\"), got: "
                        + "\"" + filter.trim() + "\""
        );
    }
}
