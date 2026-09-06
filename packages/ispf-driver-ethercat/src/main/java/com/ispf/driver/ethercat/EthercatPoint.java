package com.ispf.driver.ethercat;

import com.ispf.driver.DriverException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EtherCAT gateway lab point.
 * <p>
 * Forms: {@code slave:1}, {@code slave:1:pdo:0}, {@code 0x6000:01}.
 */
record EthercatPoint(Kind kind, int slave, int pdo, int objectIndex, int subIndex) {

    enum Kind {
        SLAVE,
        SLAVE_PDO,
        OBJECT
    }

    private static final Pattern SLAVE_PDO = Pattern.compile(
            "^slave\\s*[:=]\\s*(\\d+)\\s*[:=]\\s*pdo\\s*[:=]\\s*(\\d+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SLAVE_ONLY = Pattern.compile(
            "^slave\\s*[:=]\\s*(\\d+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OBJECT = Pattern.compile(
            "^0x([0-9a-fA-F]+)\\s*[:=]\\s*(?:0x)?([0-9a-fA-F]+)$",
            Pattern.CASE_INSENSITIVE);

    static EthercatPoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("EtherCAT lab point mapping is blank");
        }
        String trimmed = mapping.trim();
        Matcher slavePdo = SLAVE_PDO.matcher(trimmed);
        if (slavePdo.matches()) {
            int slave = Integer.parseInt(slavePdo.group(1));
            int pdo = Integer.parseInt(slavePdo.group(2));
            return create(Kind.SLAVE_PDO, slave, pdo, 0, 0);
        }
        Matcher slaveOnly = SLAVE_ONLY.matcher(trimmed);
        if (slaveOnly.matches()) {
            return create(Kind.SLAVE, Integer.parseInt(slaveOnly.group(1)), 0, 0, 0);
        }
        Matcher object = OBJECT.matcher(trimmed);
        if (object.matches()) {
            int index = Integer.parseInt(object.group(1), 16);
            int sub = Integer.parseInt(object.group(2), 16);
            return create(Kind.OBJECT, 0, 0, index, sub);
        }
        throw new DriverException(
                "Unsupported EtherCAT lab mapping"
                        + " (expected slave:1, slave:1:pdo:0, or 0x6000:01): " + mapping);
    }

    private static EthercatPoint create(Kind kind, int slave, int pdo, int objectIndex, int subIndex)
            throws DriverException {
        if (slave < 0 || slave > 65535) {
            throw new DriverException("EtherCAT lab slave out of range: " + slave);
        }
        if (pdo < 0 || pdo > 255) {
            throw new DriverException("EtherCAT lab PDO out of range: " + pdo);
        }
        if (objectIndex < 0 || objectIndex > 0xFFFF) {
            throw new DriverException("EtherCAT lab object index out of range: " + objectIndex);
        }
        if (subIndex < 0 || subIndex > 0xFF) {
            throw new DriverException("EtherCAT lab sub-index out of range: " + subIndex);
        }
        return new EthercatPoint(kind, slave, pdo, objectIndex, subIndex);
    }

    String wireToken() {
        return switch (kind) {
            case SLAVE -> "slave:" + slave;
            case SLAVE_PDO -> "slave:" + slave + ":pdo:" + pdo;
            case OBJECT -> String.format(Locale.ROOT, "0x%04X:%02X", objectIndex, subIndex);
        };
    }

    String display() {
        return wireToken().toLowerCase(Locale.ROOT);
    }

    String kindName() {
        return kind.name().toLowerCase(Locale.ROOT);
    }
}
