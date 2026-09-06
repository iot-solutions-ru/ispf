package com.ispf.driver.ethernetpowerlink;

import com.ispf.driver.DriverException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ethernet POWERLINK MN/CN lab point.
 * <p>
 * Forms: {@code node:1:obj:0x6000:01}, {@code pdo:1}.
 */
record EthernetPowerlinkPoint(Kind kind, int node, int objectIndex, int subIndex, int pdo) {

    enum Kind {
        OBJECT,
        PDO
    }

    private static final Pattern OBJECT = Pattern.compile(
            "^node\\s*[:=]\\s*(\\d+)\\s*[:=]\\s*obj\\s*[:=]\\s*0x([0-9a-fA-F]+)\\s*[:=]\\s*"
                    + "(?:0x)?([0-9a-fA-F]+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PDO = Pattern.compile(
            "^pdo\\s*[:=]\\s*(\\d+)$", Pattern.CASE_INSENSITIVE);

    static EthernetPowerlinkPoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("Ethernet POWERLINK lab point mapping is blank");
        }
        String trimmed = mapping.trim();
        Matcher object = OBJECT.matcher(trimmed);
        if (object.matches()) {
            int node = Integer.parseInt(object.group(1));
            int index = Integer.parseInt(object.group(2), 16);
            int sub = Integer.parseInt(object.group(3), 16);
            if (node < 0 || node > 239) {
                throw new DriverException("Ethernet POWERLINK lab node out of range: " + node);
            }
            if (index < 0 || index > 0xFFFF) {
                throw new DriverException("Ethernet POWERLINK lab object index out of range: " + index);
            }
            if (sub < 0 || sub > 0xFF) {
                throw new DriverException("Ethernet POWERLINK lab sub-index out of range: " + sub);
            }
            return new EthernetPowerlinkPoint(Kind.OBJECT, node, index, sub, 0);
        }
        Matcher pdo = PDO.matcher(trimmed);
        if (pdo.matches()) {
            int pdoId = Integer.parseInt(pdo.group(1));
            if (pdoId < 0 || pdoId > 0xFFFF) {
                throw new DriverException("Ethernet POWERLINK lab PDO id out of range: " + pdoId);
            }
            return new EthernetPowerlinkPoint(Kind.PDO, 0, 0, 0, pdoId);
        }
        throw new DriverException(
                "Unsupported Ethernet POWERLINK lab mapping"
                        + " (expected node:1:obj:0x6000:01 or pdo:1): " + mapping);
    }

    String wireToken() {
        return switch (kind) {
            case OBJECT -> String.format(Locale.ROOT, "node:%d:obj:0x%04X:%02X",
                    node, objectIndex, subIndex);
            case PDO -> "pdo:" + pdo;
        };
    }

    String display() {
        return wireToken().toLowerCase(Locale.ROOT);
    }

    String kindName() {
        return kind.name().toLowerCase(Locale.ROOT);
    }
}
