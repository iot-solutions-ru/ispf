package com.ispf.driver.devicenet;

import com.ispf.driver.DriverException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DeviceNet CIP gateway lab point.
 * <p>
 * Forms: {@code node:1}, {@code node:1:attr:1}, {@code class:4:inst:1:attr:3}.
 */
record DeviceNetPoint(Kind kind, int node, int cipClass, int instance, int attribute) {

    enum Kind {
        NODE,
        NODE_ATTR,
        CLASS_PATH
    }

    private static final Pattern NODE_ONLY = Pattern.compile(
            "^node\\s*[:=]\\s*(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NODE_ATTR = Pattern.compile(
            "^node\\s*[:=]\\s*(\\d+)\\s*[:=]\\s*attr\\s*[:=]\\s*(\\d+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CLASS_PATH = Pattern.compile(
            "^class\\s*[:=]\\s*(\\d+)\\s*[:=]\\s*inst\\s*[:=]\\s*(\\d+)\\s*[:=]\\s*attr\\s*[:=]\\s*(\\d+)$",
            Pattern.CASE_INSENSITIVE);

    static DeviceNetPoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("DeviceNet lab point mapping is blank");
        }
        String trimmed = mapping.trim();
        Matcher nodeAttr = NODE_ATTR.matcher(trimmed);
        if (nodeAttr.matches()) {
            int node = Integer.parseInt(nodeAttr.group(1));
            int attr = Integer.parseInt(nodeAttr.group(2));
            validateNode(node);
            validateAttr(attr);
            return new DeviceNetPoint(Kind.NODE_ATTR, node, 0, 0, attr);
        }
        Matcher nodeOnly = NODE_ONLY.matcher(trimmed);
        if (nodeOnly.matches()) {
            int node = Integer.parseInt(nodeOnly.group(1));
            validateNode(node);
            return new DeviceNetPoint(Kind.NODE, node, 0, 0, 1);
        }
        Matcher classPath = CLASS_PATH.matcher(trimmed);
        if (classPath.matches()) {
            int cipClass = Integer.parseInt(classPath.group(1));
            int instance = Integer.parseInt(classPath.group(2));
            int attr = Integer.parseInt(classPath.group(3));
            if (cipClass < 0 || cipClass > 0xFFFF) {
                throw new DriverException("DeviceNet lab class out of range: " + cipClass);
            }
            if (instance < 0 || instance > 0xFFFF) {
                throw new DriverException("DeviceNet lab instance out of range: " + instance);
            }
            validateAttr(attr);
            return new DeviceNetPoint(Kind.CLASS_PATH, 0, cipClass, instance, attr);
        }
        throw new DriverException(
                "Unsupported DeviceNet lab mapping (expected node:1, node:1:attr:1,"
                        + " or class:4:inst:1:attr:3): " + mapping);
    }

    private static void validateNode(int node) throws DriverException {
        if (node < 0 || node > 63) {
            throw new DriverException("DeviceNet lab node out of range: " + node);
        }
    }

    private static void validateAttr(int attr) throws DriverException {
        if (attr < 0 || attr > 0xFFFF) {
            throw new DriverException("DeviceNet lab attribute out of range: " + attr);
        }
    }

    String wireToken() {
        return switch (kind) {
            case NODE -> "node:" + node;
            case NODE_ATTR -> "node:" + node + ":attr:" + attribute;
            case CLASS_PATH -> "class:" + cipClass + ":inst:" + instance + ":attr:" + attribute;
        };
    }

    String display() {
        return wireToken().toLowerCase(Locale.ROOT);
    }
}
