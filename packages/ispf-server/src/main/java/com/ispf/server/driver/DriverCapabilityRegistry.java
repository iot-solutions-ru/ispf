package com.ispf.server.driver;

import java.util.Set;

/**
 * Declared runtime capabilities per driver id (see docs/DRIVERS.md).
 */
final class DriverCapabilityRegistry {

    private static final Set<String> READ = Set.of("read");
    private static final Set<String> READ_WRITE = Set.of("read", "write");

    private static final Set<String> WRITE_CAPABLE = Set.of(
            "modbus-tcp",
            "modbus-rtu",
            "modbus-udp",
            "opcua",
            "opcua-server",
            "s7",
            "snmp",
            "mqtt",
            "bacnet",
            "iec104",
            "iec104-server",
            "virtual",
            "http-server"
    );

    private DriverCapabilityRegistry() {
    }

    static Set<String> resolve(String driverId) {
        return WRITE_CAPABLE.contains(driverId) ? READ_WRITE : READ;
    }
}
