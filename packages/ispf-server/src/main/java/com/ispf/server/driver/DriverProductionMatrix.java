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

    /** Phase 25 top-20 industrial driver ids (BL-140). */
    static final List<String> TOP_20_INDUSTRIAL = List.of(
            "virtual",
            "mqtt",
            "modbus-tcp",
            "modbus-rtu",
            "modbus-udp",
            "opcua",
            "opcua-server",
            "snmp",
            "bacnet",
            "s7",
            "http",
            "flexible",
            "iec104",
            "iec104-server",
            "dnp3",
            "dlms",
            "ethernet-ip",
            "opc-da",
            "opc-bridge",
            "gps-tracker"
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
            String loopbackTestSourcePath,
            String interopGradleModule
    ) {
        Entry {
            capabilities = Set.copyOf(capabilities);
        }

        Entry(String driverId, DriverMaturity maturity, Set<Capability> capabilities, String loopbackTestSourcePath) {
            this(driverId, maturity, capabilities, loopbackTestSourcePath, null);
        }
    }

    private static final Set<Capability> POLL_OBSERVED_QUALITY = EnumSet.of(
            Capability.POLL, Capability.OBSERVED_AT, Capability.QUALITY
    );
    private static final Set<Capability> POLL_WRITE_OBSERVED = EnumSet.of(
            Capability.POLL, Capability.WRITE, Capability.OBSERVED_AT
    );
    private static final Set<Capability> POLL_WRITE_QUALITY = EnumSet.of(
            Capability.POLL, Capability.WRITE, Capability.QUALITY
    );
    private static final Set<Capability> POLL_WRITE = EnumSet.of(Capability.POLL, Capability.WRITE);
    private static final Set<Capability> POLL_OBSERVED = EnumSet.of(Capability.POLL, Capability.OBSERVED_AT);
    private static final Set<Capability> POLL_ONLY = EnumSet.of(Capability.POLL);

    private static final Map<String, Entry> ENTRIES = Map.ofEntries(
            entry("virtual", DriverMaturity.PRODUCTION, POLL_OBSERVED_QUALITY,
                    testPath("ispf-driver-virtual", "com.ispf.driver.virtual.VirtualUnifiedProfileTest"),
                    "ispf-driver-virtual"),
            entry("mqtt", DriverMaturity.PRODUCTION, POLL_WRITE_OBSERVED,
                    testPath("ispf-driver-mqtt", "com.ispf.driver.mqtt.MqttDeviceDriverTest"),
                    "ispf-driver-mqtt"),
            entry("modbus-tcp", DriverMaturity.PRODUCTION, POLL_WRITE_OBSERVED,
                    testPath("ispf-driver-modbus", "com.ispf.driver.modbus.ModbusTcpDeviceDriverTest"),
                    "ispf-driver-modbus"),
            entry("modbus-rtu", DriverMaturity.PRODUCTION, POLL_WRITE_OBSERVED,
                    testPath("ispf-driver-modbus-rtu", "com.ispf.driver.modbusrtu.ModbusRtuDeviceDriverTest"),
                    "ispf-driver-modbus-rtu"),
            entry("modbus-udp", DriverMaturity.PRODUCTION, POLL_WRITE_OBSERVED,
                    testPath("ispf-driver-modbus", "com.ispf.driver.modbus.ModbusTcpDeviceDriverTest"),
                    "ispf-driver-modbus-udp"),
            entry("opcua", DriverMaturity.PRODUCTION, EnumSet.of(
                    Capability.POLL, Capability.SUBSCRIBE, Capability.WRITE, Capability.DISCOVERY,
                    Capability.OBSERVED_AT, Capability.QUALITY
            ),
                    testPath("ispf-driver-opcua", "com.ispf.driver.opcua.OpcUaDeviceDriverTest"),
                    "ispf-driver-opcua"),
            entry("opcua-server", DriverMaturity.PRODUCTION, POLL_WRITE_QUALITY,
                    testPath("ispf-driver-opcua-server", "com.ispf.driver.opcuaserver.OpcUaServerDeviceDriverTest"),
                    "ispf-driver-opcua-server"),
            entry("s7", DriverMaturity.PRODUCTION, POLL_WRITE_OBSERVED,
                    testPath("ispf-driver-s7", "com.ispf.driver.s7.S7DeviceDriverTest"),
                    "ispf-driver-s7"),
            entry("snmp", DriverMaturity.PRODUCTION, POLL_WRITE_OBSERVED,
                    testPath("ispf-driver-snmp", "com.ispf.driver.snmp.SnmpDeviceDriverTest"),
                    "ispf-driver-snmp"),
            entry("http", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-http", "com.ispf.driver.http.HttpDeviceDriverTest"),
                    "ispf-driver-http"),
            entry("bacnet", DriverMaturity.PRODUCTION, POLL_WRITE_OBSERVED,
                    testPath("ispf-driver-bacnet", "com.ispf.driver.bacnet.BacnetDeviceDriverNetworkTest"),
                    "ispf-driver-bacnet"),
            entry("iec104", DriverMaturity.PRODUCTION, POLL_WRITE_OBSERVED,
                    testPath("ispf-driver-iec104", "com.ispf.driver.iec104.Iec104DeviceDriverTest"),
                    "ispf-driver-iec104"),
            entry("iec104-server", DriverMaturity.BETA, POLL_ONLY,
                    testPath("ispf-driver-iec104-server", "com.ispf.driver.iec104server.Iec104ServerPointTest"),
                    "ispf-driver-iec104-server"),
            entry("dnp3", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-dnp3", "com.ispf.driver.dnp3.Dnp3DeviceDriverTest"),
                    "ispf-driver-dnp3"),
            entry("dlms", DriverMaturity.PRODUCTION, POLL_WRITE_OBSERVED,
                    testPath("ispf-driver-dlms", "com.ispf.driver.dlms.DlmsDeviceDriverTest"),
                    "ispf-driver-dlms"),
            entry("cwmp", DriverMaturity.PRODUCTION, POLL_WRITE_OBSERVED,
                    testPath("ispf-driver-cwmp", "com.ispf.driver.cwmp.CwmpDeviceDriverTest"),
                    "ispf-driver-cwmp"),
            entry("ethernet-ip", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-ethernet-ip", "com.ispf.driver.ethernetip.EthernetIpDeviceDriverTest"),
                    "ispf-driver-ethernet-ip"),
            entry("opc-da", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-opc-da", "com.ispf.driver.opcda.OpcDaDeviceDriverTest"),
                    "ispf-driver-opc-da"),
            entry("opc-bridge", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-opc-bridge", "com.ispf.driver.opcbridge.OpcBridgeDeviceDriverTest"),
                    "ispf-driver-opc-bridge"),
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
                    testPath("ispf-driver-flexible", "com.ispf.driver.flexible.FlexiblePointTest"),
                    "ispf-driver-flexible"),
            entry("gps-tracker", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-gps-tracker", "com.ispf.driver.gpstracker.GpsTrackerPointTest"),
                    "ispf-driver-gps-tracker"),
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

    static Optional<String> resolveInteropGradleModule(String driverId) {
        return entry(driverId).map(Entry::interopGradleModule).filter(s -> s != null && !s.isBlank());
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
        return entry(driverId, maturity, capabilities, loopbackTestSourcePath, null);
    }

    private static Map.Entry<String, Entry> entry(
            String driverId,
            DriverMaturity maturity,
            Set<Capability> capabilities,
            String loopbackTestSourcePath,
            String interopGradleModule
    ) {
        return Map.entry(driverId, new Entry(driverId, maturity, capabilities, loopbackTestSourcePath, interopGradleModule));
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
