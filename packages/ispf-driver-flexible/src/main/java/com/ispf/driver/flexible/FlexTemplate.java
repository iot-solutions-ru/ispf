package com.ispf.driver.flexible;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders request templates with {@code ${key}} substitution and {@code \\xHH} escapes.
 */
final class FlexTemplate {

    private static final Pattern VAR = Pattern.compile("\\$\\{([^}]+)}");

    private FlexTemplate() {
    }

    static byte[] render(String template, Map<String, String> variables) {
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("Request template is blank");
        }
        Matcher matcher = VAR.matcher(template);
        StringBuilder resolved = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String value = variables.getOrDefault(key, "");
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(resolved);
        return decodeEscapes(resolved.toString());
    }

    static String renderKey(String template, Map<String, String> variables) {
        return FlexibleDeviceDriver.bytesToHex(render(template, variables), render(template, variables).length);
    }

    private static byte[] decodeEscapes(String value) {
        List<Byte> bytes = new ArrayList<>(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 3 < value.length() && (value.charAt(i + 1) == 'x' || value.charAt(i + 1) == 'X')) {
                String hex = value.substring(i + 2, i + 4);
                bytes.add((byte) Integer.parseInt(hex, 16));
                i += 3;
            } else {
                bytes.add((byte) (value.charAt(i) & 0xFF));
            }
        }
        byte[] out = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); i++) {
            out[i] = bytes.get(i);
        }
        return out;
    }

    static String asPrintable(byte[] bytes) {
        return new String(bytes, StandardCharsets.US_ASCII);
    }
}
