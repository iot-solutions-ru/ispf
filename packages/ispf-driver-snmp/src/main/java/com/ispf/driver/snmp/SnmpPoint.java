package com.ispf.driver.snmp;

import com.ispf.driver.DriverException;

/**
 * Parsed SNMP OID mapping. Format: {@code oid}, {@code oid:VALUE_KIND}, or {@code oid:VALUE_KIND:optional}.
 * VALUE_KIND: AUTO (default), INTEGER, STRING, BOOLEAN.
 * {@code optional} — missing OID does not fail the poll cycle (logged once at DEBUG).
 */
record SnmpPoint(String oid, ValueKind valueKind, boolean optional) {

    SnmpPoint(String oid, ValueKind valueKind) {
        this(oid, valueKind, false);
    }

    enum ValueKind {
        AUTO, INTEGER, STRING, BOOLEAN
    }

    static SnmpPoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("SNMP mapping is empty");
        }
        String trimmed = mapping.trim();
        String[] segments = trimmed.split(":", -1);
        if (segments.length == 1) {
            return new SnmpPoint(trimmed, ValueKind.AUTO);
        }
        String oid = segments[0].trim();
        if (oid.isBlank()) {
            throw new DriverException("SNMP OID is required in mapping: " + mapping);
        }
        boolean optional = "optional".equalsIgnoreCase(segments[segments.length - 1].trim());
        String kindRaw = optional
                ? segments[segments.length - 2].trim()
                : segments[segments.length - 1].trim();
        if (optional && segments.length < 3) {
            return new SnmpPoint(oid, ValueKind.AUTO, true);
        }
        try {
            return new SnmpPoint(oid, ValueKind.valueOf(kindRaw.toUpperCase()), optional);
        } catch (IllegalArgumentException e) {
            throw new DriverException("Unknown SNMP value kind in mapping: " + mapping, e);
        }
    }
}
