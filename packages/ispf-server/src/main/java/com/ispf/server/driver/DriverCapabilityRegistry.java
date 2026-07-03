package com.ispf.server.driver;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Declared runtime capabilities per driver id (see docs/DRIVERS.md).
 * Primary source: {@link DriverProductionMatrix} (ADR-0022).
 */
final class DriverCapabilityRegistry {

    private static final Set<String> READ = Set.of("read");
    private static final Set<String> READ_WRITE = Set.of("read", "write");

    private static final Set<String> LEGACY_WRITE_CAPABLE = Set.of(
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
            "dlms",
            "virtual",
            "http-server"
    );

    private DriverCapabilityRegistry() {
    }

    static Set<String> resolve(String driverId) {
        if (DriverProductionMatrix.entry(driverId).isPresent()) {
            return mapMatrixCapabilities(DriverProductionMatrix.resolveCapabilities(driverId));
        }
        return LEGACY_WRITE_CAPABLE.contains(driverId) ? READ_WRITE : READ;
    }

    private static Set<String> mapMatrixCapabilities(Set<DriverProductionMatrix.Capability> capabilities) {
        Set<String> mapped = new LinkedHashSet<>();
        for (DriverProductionMatrix.Capability capability : capabilities) {
            mapped.add(toApiName(capability));
        }
        if (mapped.isEmpty()) {
            mapped.add("read");
        }
        return Set.copyOf(mapped);
    }

    private static String toApiName(DriverProductionMatrix.Capability capability) {
        return switch (capability) {
            case POLL -> "read";
            case WRITE -> "write";
            case SUBSCRIBE -> "subscribe";
            case DISCOVERY -> "discovery";
            case OBSERVED_AT -> "observed_at";
            case QUALITY -> "quality";
        };
    }
}
