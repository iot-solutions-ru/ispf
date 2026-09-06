package com.ispf.driver.matter;

import com.ispf.driver.DriverException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Matter/CHIP controller gateway lab point.
 * <p>
 * Forms: {@code node:1:ep:1:cluster:OnOff:attr:OnOff}, {@code node:1:cmd:On}.
 */
record MatterPoint(Kind kind, String display, int node, int endpoint, String cluster, String attribute,
                   String command) {

    enum Kind {
        ATTR,
        CMD
    }

    private static final Pattern ATTR = Pattern.compile(
            "^node\\s*[:=]\\s*(\\d+)"
                    + "\\s*[:=]\\s*ep\\s*[:=]\\s*(\\d+)"
                    + "\\s*[:=]\\s*cluster\\s*[:=]\\s*([A-Za-z0-9_]+)"
                    + "\\s*[:=]\\s*attr\\s*[:=]\\s*([A-Za-z0-9_]+)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CMD = Pattern.compile(
            "^node\\s*[:=]\\s*(\\d+)"
                    + "\\s*[:=]\\s*cmd\\s*[:=]\\s*([A-Za-z0-9_]+)$",
            Pattern.CASE_INSENSITIVE);

    static MatterPoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("Matter point mapping is blank");
        }
        String trimmed = mapping.trim();
        Matcher attr = ATTR.matcher(trimmed);
        if (attr.matches()) {
            int node = Integer.parseInt(attr.group(1));
            int ep = Integer.parseInt(attr.group(2));
            if (node < 1 || ep < 1) {
                throw new DriverException("Matter node/endpoint out of range: " + mapping);
            }
            String cluster = attr.group(3);
            String attribute = attr.group(4);
            String display = "node:" + node + ":ep:" + ep + ":cluster:" + cluster + ":attr:" + attribute;
            return new MatterPoint(Kind.ATTR, display, node, ep, cluster, attribute, null);
        }
        Matcher cmd = CMD.matcher(trimmed);
        if (cmd.matches()) {
            int node = Integer.parseInt(cmd.group(1));
            if (node < 1) {
                throw new DriverException("Matter node out of range: " + node);
            }
            String command = cmd.group(2);
            String display = "node:" + node + ":cmd:" + command;
            return new MatterPoint(Kind.CMD, display, node, -1, null, null, command);
        }
        throw new DriverException(
                "Unsupported Matter mapping (expected node:1:ep:1:cluster:OnOff:attr:OnOff"
                        + " or node:1:cmd:On): " + mapping);
    }

    boolean writable() {
        return true;
    }

    String wireToken() {
        return display;
    }

    String kindToken() {
        return kind == Kind.ATTR ? "attr" : "cmd";
    }
}
