package com.ispf.driver.jmx;

/**
 * Point mapping: {@code objectName::attribute[.compositeKey]} or
 * {@code objectName:attribute[:compositeKey]}.
 */
public record JmxPoint(String objectName, String attribute, String compositeKey) {

    public static JmxPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("JMX point mapping is blank");
        }
        String trimmed = raw.trim();
        if (trimmed.contains("::")) {
            return parseDoubleColon(trimmed);
        }
        return parseSimpleColon(trimmed);
    }

    private static JmxPoint parseDoubleColon(String trimmed) {
        int sep = trimmed.indexOf("::");
        String objectName = trimmed.substring(0, sep).trim();
        String attrPart = trimmed.substring(sep + 2).trim();
        if (objectName.isEmpty() || attrPart.isEmpty()) {
            throw new IllegalArgumentException("Invalid JMX point mapping: " + trimmed);
        }
        int dot = attrPart.indexOf('.');
        if (dot > 0) {
            return new JmxPoint(objectName, attrPart.substring(0, dot), attrPart.substring(dot + 1));
        }
        return new JmxPoint(objectName, attrPart, null);
    }

    private static JmxPoint parseSimpleColon(String trimmed) {
        int lastColon = trimmed.lastIndexOf(':');
        if (lastColon <= 0) {
            throw new IllegalArgumentException("Invalid JMX point mapping: " + trimmed);
        }
        int secondLast = trimmed.lastIndexOf(':', lastColon - 1);
        if (secondLast > 0) {
            return new JmxPoint(
                    trimmed.substring(0, secondLast).trim(),
                    trimmed.substring(secondLast + 1, lastColon).trim(),
                    trimmed.substring(lastColon + 1).trim()
            );
        }
        return new JmxPoint(trimmed.substring(0, lastColon).trim(), trimmed.substring(lastColon + 1).trim(), null);
    }
}
