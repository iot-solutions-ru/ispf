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
            entry("iec104-server", DriverMaturity.PRODUCTION, POLL_WRITE_QUALITY,
                    testPath("ispf-driver-iec104-server", "com.ispf.driver.iec104server.Iec104ServerDeviceDriverTest"),
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
            entry("ethernet-ip", DriverMaturity.BETA, POLL_ONLY,
                    testPath("ispf-driver-ethernet-ip", "com.ispf.driver.ethernetip.EthernetIpDeviceDriverTest"),
                    "ispf-driver-ethernet-ip"),
            entry("opc-da", DriverMaturity.BETA, POLL_ONLY,
                    testPath("ispf-driver-opc-da", "com.ispf.driver.opcda.OpcDaDeviceDriverTest"),
                    "ispf-driver-opc-da"),
            entry("opc-bridge", DriverMaturity.BETA, POLL_ONLY,
                    testPath("ispf-driver-opc-bridge", "com.ispf.driver.opcbridge.OpcBridgeDeviceDriverTest"),
                    "ispf-driver-opc-bridge"),
            entry("corba", DriverMaturity.BETA, POLL_ONLY,
                    testPath("ispf-driver-corba", "com.ispf.driver.corba.CorbaDeviceDriverTest")),
            entry("vmware", DriverMaturity.BETA, POLL_ONLY,
                    testPath("ispf-driver-vmware", "com.ispf.driver.vmware.VmwareDeviceDriverTest")),
            entry("smi-s", DriverMaturity.BETA, POLL_ONLY,
                    testPath("ispf-driver-smis", "com.ispf.driver.smis.SmisDeviceDriverTest")),
            entry("wmi", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-wmi", "com.ispf.driver.wmi.WmiDeviceDriverTest"),
                    "ispf-driver-wmi"),
            entry("odbc", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-odbc", "com.ispf.driver.odbc.OdbcDeviceDriverTest"),
                    "ispf-driver-odbc"),
            entry("graph-db", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-graph-db", "com.ispf.driver.graphdb.GraphDbDeviceDriverTest"),
                    "ispf-driver-graph-db"),
            entry("flexible", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-flexible", "com.ispf.driver.flexible.FlexiblePointTest"),
                    "ispf-driver-flexible"),
            entry("gps-tracker", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-gps-tracker", "com.ispf.driver.gpstracker.GpsTrackerPointTest"),
                    "ispf-driver-gps-tracker"),
            entry("haystack", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-haystack", "com.ispf.driver.haystack.HaystackDeviceDriverTest"),
                    "ispf-driver-haystack"),
            entry("kafka", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-kafka", "com.ispf.driver.kafka.KafkaDeviceDriverTest"),
                    "ispf-driver-kafka"),
            entry("coap", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-coap", "com.ispf.driver.coap.CoapDeviceDriverTest"),
                    "ispf-driver-coap"),
            entry("icmp", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-icmp", "com.ispf.driver.icmp.IcmpDeviceDriverTest"),
                    "ispf-driver-icmp"),
            entry("ip-host", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-ip-host", "com.ispf.driver.iphost.IpHostDeviceDriverTest"),
                    "ispf-driver-ip-host"),
            entry("telnet", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-telnet", "com.ispf.driver.telnet.TelnetDeviceDriverTest"),
                    "ispf-driver-telnet"),
            entry("modem-at", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-modem-at", "com.ispf.driver.modemat.ModemAtDeviceDriverTest"),
                    "ispf-driver-modem-at"),
            entry("ssh", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-ssh", "com.ispf.driver.ssh.SshDeviceDriverTest"),
                    "ispf-driver-ssh"),
            entry("file", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-file", "com.ispf.driver.file.FileDeviceDriverTest"),
                    "ispf-driver-file"),
            entry("folder", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-folder", "com.ispf.driver.folder.FolderDeviceDriverTest"),
                    "ispf-driver-folder"),
            entry("application", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-application", "com.ispf.driver.application.ApplicationDeviceDriverTest"),
                    "ispf-driver-application"),
            entry("imap", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-imap", "com.ispf.driver.imap.ImapDeviceDriverTest"),
                    "ispf-driver-imap"),
            entry("pop3", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-pop3", "com.ispf.driver.pop3.Pop3DeviceDriverTest"),
                    "ispf-driver-pop3"),
            entry("soap", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-soap", "com.ispf.driver.soap.SoapDeviceDriverTest"),
                    "ispf-driver-soap"),
            entry("web-transaction", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-web-transaction", "com.ispf.driver.webtransaction.WebTransactionDeviceDriverTest"),
                    "ispf-driver-web-transaction"),
            entry("http-server", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-http-server", "com.ispf.driver.httpserver.HttpServerDeviceDriverTest"),
                    "ispf-driver-http-server"),
            entry("jdbc", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-jdbc", "com.ispf.driver.jdbc.JdbcDeviceDriverTest"),
                    "ispf-driver-jdbc"),
            entry("jms", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-jms", "com.ispf.driver.jms.JmsDeviceDriverTest"),
                    "ispf-driver-jms"),
            entry("sip", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-sip", "com.ispf.driver.sip.SipDeviceDriverTest"),
                    "ispf-driver-sip"),
            entry("asterisk", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-asterisk", "com.ispf.driver.asterisk.AsteriskDeviceDriverTest"),
                    "ispf-driver-asterisk"),
            entry("radius", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-radius", "com.ispf.driver.radius.RadiusDeviceDriverTest"),
                    "ispf-driver-radius"),
            entry("ldap", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-ldap", "com.ispf.driver.ldap.LdapDeviceDriverTest"),
                    "ispf-driver-ldap"),
            entry("jmx", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-jmx", "com.ispf.driver.jmx.JmxDeviceDriverTest"),
                    "ispf-driver-jmx"),
            entry("nmea", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-nmea", "com.ispf.driver.nmea.NmeaDeviceDriverTest"),
                    "ispf-driver-nmea"),
            entry("message-stream", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-message-stream", "com.ispf.driver.messagestream.MessageStreamDeviceDriverTest"),
                    "ispf-driver-message-stream"),
            entry("dhcp", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-dhcp", "com.ispf.driver.dhcp.DhcpDeviceDriverTest"),
                    "ispf-driver-dhcp"),
            entry("ingress-syslog", DriverMaturity.PRODUCTION, POLL_OBSERVED,
                    testPath("ispf-driver-ingress-syslog", "com.ispf.driver.ingress.syslog.SyslogIngressDeviceDriverTest"),
                    "ispf-driver-ingress-syslog"),
            entry("ingress-snmp-trap", DriverMaturity.PRODUCTION, POLL_OBSERVED,
                    testPath("ispf-driver-ingress-snmp-trap", "com.ispf.driver.ingress.snmptrap.SnmpTrapIngressDeviceDriverTest"),
                    "ispf-driver-ingress-snmp-trap"),
            entry("ingress-sflow", DriverMaturity.PRODUCTION, POLL_OBSERVED,
                    testPath("ispf-driver-ingress-sflow", "com.ispf.driver.ingress.sflow.SflowIngressDeviceDriverTest"),
                    "ispf-driver-ingress-sflow"),
            entry("omron-fins", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-omron-fins", "com.ispf.driver.omronfins.OmronFinsDeviceDriverTest"),
                    "ispf-driver-omron-fins"),
            entry("mbus", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-mbus", "com.ispf.driver.mbus.MbusDeviceDriverTest"),
                    "ispf-driver-mbus"),
            entry("smpp", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-smpp", "com.ispf.driver.smpp.SmppDeviceDriverTest"),
                    "ispf-driver-smpp"),
            entry("xmpp", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-xmpp", "com.ispf.driver.xmpp.XmppDeviceDriverTest"),
                    "ispf-driver-xmpp"),
            entry("ipmi", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-ipmi", "com.ispf.driver.ipmi.IpmiDeviceDriverTest"),
                    "ispf-driver-ipmi")
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
