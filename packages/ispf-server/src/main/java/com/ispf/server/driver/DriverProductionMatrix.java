package com.ispf.server.driver;

import com.ispf.driver.DriverMaturity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Authoritative production-readiness matrix (ADR-0022, BL-78).
 */
final class DriverProductionMatrix {

    /** ADR-0022 top-10 industrial driver ids (BL-85). */
    static final List<String> TOP_10_INDUSTRIAL = List.of(
            "virtual",
            "mqtt",
            "modbus-tcp",
            "opcua",
            "snmp",
            "bacnet",
            "s7",
            "http",
            "flexible",
            "modbus-rtu"
    );

    enum Capability {
        POLL,
        SUBSCRIBE,
        WRITE,
        DISCOVERY,
        QUALITY,
        OBSERVED_AT
    }

    record Entry(
            String driverId,
            DriverMaturity maturity,
            Set<Capability> capabilities,
            String loopbackTestSourcePath
    ) {
        Entry {
            capabilities = Set.copyOf(capabilities);
        }
    }

    private static final Set<Capability> POLL_WRITE_OBSERVED = EnumSet.of(
            Capability.POLL, Capability.WRITE, Capability.OBSERVED_AT
    );
    private static final Set<Capability> POLL_WRITE = EnumSet.of(Capability.POLL, Capability.WRITE);
    private static final Set<Capability> POLL_OBSERVED = EnumSet.of(Capability.POLL, Capability.OBSERVED_AT);
    private static final Set<Capability> POLL_ONLY = EnumSet.of(Capability.POLL);

    private static final Map<String, Entry> ENTRIES = Map.ofEntries(
            entry("virtual", DriverMaturity.PRODUCTION, POLL_OBSERVED,
                    testPath("ispf-driver-virtual", "com.ispf.driver.virtual.VirtualUnifiedProfileTest")),
            entry("mqtt", DriverMaturity.PRODUCTION, POLL_WRITE_OBSERVED,
                    testPath("ispf-driver-mqtt", "com.ispf.driver.mqtt.MqttDeviceDriverTest")),
            entry("modbus-tcp", DriverMaturity.PRODUCTION, POLL_WRITE_OBSERVED,
                    testPath("ispf-driver-modbus", "com.ispf.driver.modbus.ModbusTcpDeviceDriverTest")),
            entry("modbus-rtu", DriverMaturity.PRODUCTION, POLL_WRITE_OBSERVED,
                    testPath("ispf-driver-modbus-rtu", "com.ispf.driver.modbusrtu.ModbusRtuDeviceDriverTest")),
            entry("modbus-udp", DriverMaturity.PRODUCTION, POLL_WRITE_OBSERVED,
                    testPath("ispf-driver-modbus", "com.ispf.driver.modbus.ModbusTcpDeviceDriverTest")),
            entry("opcua", DriverMaturity.PRODUCTION, EnumSet.of(
                    Capability.POLL, Capability.SUBSCRIBE, Capability.WRITE, Capability.DISCOVERY, Capability.OBSERVED_AT
            ),
                    testPath("ispf-driver-opcua", "com.ispf.driver.opcua.OpcUaDeviceDriverTest")),
            entry("opcua-server", DriverMaturity.PRODUCTION, POLL_WRITE,
                    testPath("ispf-driver-opcua-server", "com.ispf.driver.opcuaserver.OpcUaServerPointTest")),
            entry("s7", DriverMaturity.PRODUCTION, POLL_WRITE_OBSERVED,
                    testPath("ispf-driver-s7", "com.ispf.driver.s7.S7DeviceDriverTest")),
            entry("snmp", DriverMaturity.PRODUCTION, POLL_WRITE_OBSERVED,
                    testPath("ispf-driver-snmp", "com.ispf.driver.snmp.SnmpDeviceDriverTest")),
            entry("http", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-http", "com.ispf.driver.http.HttpDeviceDriverTest")),
            entry("bacnet", DriverMaturity.PRODUCTION, POLL_WRITE_OBSERVED,
                    testPath("ispf-driver-bacnet", "com.ispf.driver.bacnet.BacnetDeviceDriverNetworkTest")),
            entry("iec104", DriverMaturity.BETA, POLL_WRITE,
                    testPath("ispf-driver-iec104", "com.ispf.driver.iec104.Iec104DeviceDriverTest")),
            entry("iec104-server", DriverMaturity.BETA, POLL_ONLY,
                    testPath("ispf-driver-iec104-server", "com.ispf.driver.iec104server.Iec104ServerPointTest")),
            entry("dnp3", DriverMaturity.BETA, POLL_ONLY,
                    testPath("ispf-driver-dnp3", "com.ispf.driver.dnp3.Dnp3DeviceDriverTest")),
            entry("dlms", DriverMaturity.BETA, POLL_WRITE,
                    testPath("ispf-driver-dlms", "com.ispf.driver.dlms.DlmsDeviceDriverTest")),
            entry("cwmp", DriverMaturity.BETA, POLL_WRITE,
                    testPath("ispf-driver-cwmp", "com.ispf.driver.cwmp.CwmpDeviceDriverTest")),
            entry("ethernet-ip", DriverMaturity.BETA, POLL_ONLY,
                    testPath("ispf-driver-ethernet-ip", "com.ispf.driver.ethernetip.EthernetIpDeviceDriverTest")),
            entry("opc-da", DriverMaturity.BETA, POLL_ONLY,
                    testPath("ispf-driver-opc-da", "com.ispf.driver.opcda.OpcDaDeviceDriverTest")),
            entry("opc-bridge", DriverMaturity.BETA, POLL_ONLY,
                    testPath("ispf-driver-opc-bridge", "com.ispf.driver.opcbridge.OpcBridgeDeviceDriverTest")),
            entry("corba", DriverMaturity.BETA, POLL_ONLY,
                    testPath("ispf-driver-corba", "com.ispf.driver.corba.CorbaDeviceDriverTest")),
            entry("vmware", DriverMaturity.BETA, POLL_ONLY,
                    testPath("ispf-driver-vmware", "com.ispf.driver.vmware.VmwareDeviceDriverTest")),
            entry("smi-s", DriverMaturity.BETA, POLL_ONLY,
                    testPath("ispf-driver-smis", "com.ispf.driver.smis.SmisDeviceDriverTest")),
            entry("wmi", DriverMaturity.BETA, POLL_ONLY,
                    testPath("ispf-driver-wmi", "com.ispf.driver.wmi.WmiPointTest")),
            entry("odbc", DriverMaturity.BETA, POLL_ONLY, null),
            entry("graph-db", DriverMaturity.BETA, POLL_ONLY,
                    testPath("ispf-driver-graph-db", "com.ispf.driver.graphdb.GraphDbPointTest")),
            entry("flexible", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-flexible", "com.ispf.driver.flexible.FlexiblePointTest")),
            entry("gps-tracker", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-gps-tracker", "com.ispf.driver.gpstracker.GpsTrackerPointTest")),
            entry("haystack", DriverMaturity.BETA, POLL_ONLY,
                    testPath("ispf-driver-haystack", "com.ispf.driver.haystack.HaystackDeviceDriverTest"))
    );

    private DriverProductionMatrix() {
    }

    static DriverMaturity resolveMaturity(String driverId) {
        Entry entry = ENTRIES.get(driverId);
        if (entry != null) {
            return entry.maturity();
        }
        return DriverMaturity.BETA;
    }

    static Set<Capability> resolveCapabilities(String driverId) {
        Entry entry = ENTRIES.get(driverId);
        if (entry == null) {
            return POLL_ONLY;
        }
        return entry.capabilities();
    }

    static Optional<Entry> entry(String driverId) {
        return Optional.ofNullable(ENTRIES.get(driverId));
    }

    static Map<String, Entry> entries() {
        return ENTRIES;
    }

    private static Map.Entry<String, Entry> entry(
            String driverId,
            DriverMaturity maturity,
            Set<Capability> capabilities,
            String loopbackTestSourcePath
    ) {
        return Map.entry(driverId, new Entry(driverId, maturity, capabilities, loopbackTestSourcePath));
    }

    private static String testPath(String module, String className) {
        return "packages/" + module + "/src/test/java/" + className.replace('.', '/') + ".java";
    }

    static boolean loopbackTestSourceExists(Entry entry) {
        if (entry.loopbackTestSourcePath() == null || entry.loopbackTestSourcePath().isBlank()) {
            return false;
        }
        Path relative = Path.of(entry.loopbackTestSourcePath());
        Path fromModule = Path.of("..", "..", entry.loopbackTestSourcePath()).normalize();
        return Files.exists(relative) || Files.exists(fromModule);
    }
}
