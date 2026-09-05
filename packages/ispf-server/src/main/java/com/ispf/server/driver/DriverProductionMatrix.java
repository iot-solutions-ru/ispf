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
            entry("http", DriverMaturity.PRODUCTION, POLL_WRITE_OBSERVED,
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
            entry("ethernet-ip", DriverMaturity.PRODUCTION, POLL_WRITE_QUALITY,
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
            entry("vmware", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-vmware", "com.ispf.driver.vmware.VmwareDeviceDriverTest"),
                    "ispf-driver-vmware"),
            entry("smi-s", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-smis", "com.ispf.driver.smis.SmisDeviceDriverTest"),
                    "ispf-driver-smis"),
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
            entry("ssh", DriverMaturity.PRODUCTION, POLL_WRITE,
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
            entry("smpp", DriverMaturity.PRODUCTION, POLL_WRITE,
                    testPath("ispf-driver-smpp", "com.ispf.driver.smpp.SmppDeviceDriverTest"),
                    "ispf-driver-smpp"),
            entry("xmpp", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-xmpp", "com.ispf.driver.xmpp.XmppDeviceDriverTest"),
                    "ispf-driver-xmpp"),
            entry("ipmi", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-ipmi", "com.ispf.driver.ipmi.IpmiDeviceDriverTest"),
                    "ispf-driver-ipmi"),
            // Notification / file packs previously default-BETA while docs claimed PRODUCTION (OT Trust audit).
            entry("email", DriverMaturity.PRODUCTION, POLL_WRITE,
                    testPath("ispf-driver-email", "com.ispf.driver.email.EmailDeviceDriverTest"),
                    "ispf-driver-email"),
            entry("sms", DriverMaturity.PRODUCTION, POLL_WRITE,
                    testPath("ispf-driver-sms", "com.ispf.driver.sms.SmsDeviceDriverTest"),
                    "ispf-driver-sms"),
            entry("webhook", DriverMaturity.PRODUCTION, POLL_WRITE,
                    testPath("ispf-driver-webhook", "com.ispf.driver.webhook.WebhookDeviceDriverTest"),
                    "ispf-driver-webhook"),
            entry("smb", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-smb", "com.ispf.driver.smb.SmbPointTest"),
                    "ispf-driver-smb"),
            // OT Trust Wave 2 — honest codec promotions (loopback tests, no stub javadoc).
            entry("redis", DriverMaturity.PRODUCTION, POLL_WRITE,
                    testPath("ispf-driver-redis", "com.ispf.driver.redis.RedisDeviceDriverTest"),
                    "ispf-driver-redis"),
            entry("mitsubishi-slmp", DriverMaturity.PRODUCTION, POLL_WRITE,
                    testPath("ispf-driver-mitsubishi-slmp", "com.ispf.driver.mitsubishislmp.MitsubishiSlmpDeviceDriverTest"),
                    "ispf-driver-mitsubishi-slmp"),
            entry("yaskawa-memobus", DriverMaturity.PRODUCTION, POLL_WRITE,
                    testPath("ispf-driver-yaskawa-memobus", "com.ispf.driver.yaskawamemobus.YaskawaMemobusDeviceDriverTest"),
                    "ispf-driver-yaskawa-memobus"),
            entry("sparkplug-b", DriverMaturity.PRODUCTION, POLL_WRITE,
                    testPath("ispf-driver-sparkplug-b", "com.ispf.driver.sparkplugb.SparkplugBDeviceDriverTest"),
                    "ispf-driver-sparkplug-b"),
            // OT Trust Wave 3 — clean-room Apache-2.0 codecs (loopback-tested).
            entry("beckhoff-ads", DriverMaturity.PRODUCTION, POLL_WRITE,
                    testPath("ispf-driver-beckhoff-ads", "com.ispf.driver.beckhoffads.BeckhoffAdsDeviceDriverTest"),
                    "ispf-driver-beckhoff-ads"),
            entry("mitsubishi-melsec", DriverMaturity.PRODUCTION, POLL_WRITE,
                    testPath("ispf-driver-mitsubishi-melsec", "com.ispf.driver.mitsubishimelsec.MitsubishiMelsecDeviceDriverTest"),
                    "ispf-driver-mitsubishi-melsec"),
            entry("iec62056", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-iec62056", "com.ispf.driver.iec62056.Iec62056DeviceDriverTest"),
                    "ispf-driver-iec62056"),
            entry("ieee2030-5", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-ieee2030-5", "com.ispf.driver.ieee20305.Ieee20305DeviceDriverTest"),
                    "ispf-driver-ieee2030-5"),
            entry("mqtt-sn", DriverMaturity.PRODUCTION, POLL_WRITE,
                    testPath("ispf-driver-mqtt-sn", "com.ispf.driver.mqttsn.MqttSnDeviceDriverTest"),
                    "ispf-driver-mqtt-sn"),
            entry("nats", DriverMaturity.PRODUCTION, POLL_WRITE,
                    testPath("ispf-driver-nats", "com.ispf.driver.nats.NatsDeviceDriverTest"),
                    "ispf-driver-nats"),
            entry("pulsar", DriverMaturity.PRODUCTION, POLL_WRITE,
                    testPath("ispf-driver-pulsar", "com.ispf.driver.pulsar.PulsarDeviceDriverTest"),
                    "ispf-driver-pulsar"),
            entry("onvif", DriverMaturity.PRODUCTION, POLL_WRITE,
                    testPath("ispf-driver-onvif", "com.ispf.driver.onvif.OnvifDeviceDriverTest"),
                    "ispf-driver-onvif"),
            entry("mtconnect", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-mtconnect", "com.ispf.driver.mtconnect.MtconnectDeviceDriverTest"),
                    "ispf-driver-mtconnect"),
            entry("knx", DriverMaturity.PRODUCTION, POLL_WRITE,
                    testPath("ispf-driver-knx", "com.ispf.driver.knx.KnxDeviceDriverTest"),
                    "ispf-driver-knx"),
            entry("lwm2m", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-lwm2m", "com.ispf.driver.lwm2m.Lwm2mDeviceDriverTest"),
                    "ispf-driver-lwm2m"),
            entry("websocket", DriverMaturity.PRODUCTION, POLL_WRITE,
                    testPath("ispf-driver-websocket", "com.ispf.driver.websocket.WebsocketDeviceDriverTest"),
                    "ispf-driver-websocket"),
            entry("graphql", DriverMaturity.PRODUCTION, POLL_WRITE,
                    testPath("ispf-driver-graphql", "com.ispf.driver.graphql.GraphqlDeviceDriverTest"),
                    "ispf-driver-graphql"),
            // OT Trust Wave 3b — remaining clean-room codecs from parallel agents.
            entry("ocpp", DriverMaturity.PRODUCTION, POLL_WRITE,
                    testPath("ispf-driver-ocpp", "com.ispf.driver.ocpp.OcppDeviceDriverTest"),
                    "ispf-driver-ocpp"),
            entry("odata", DriverMaturity.PRODUCTION, POLL_WRITE,
                    testPath("ispf-driver-odata", "com.ispf.driver.odata.OdataDeviceDriverTest"),
                    "ispf-driver-odata"),
            entry("grpc", DriverMaturity.PRODUCTION, POLL_WRITE,
                    testPath("ispf-driver-grpc", "com.ispf.driver.grpc.GrpcJsonDeviceDriverTest"),
                    "ispf-driver-grpc"),
            entry("openadr", DriverMaturity.PRODUCTION, POLL_WRITE,
                    testPath("ispf-driver-openadr", "com.ispf.driver.openadr.OpenadrDeviceDriverTest"),
                    "ispf-driver-openadr"),
            entry("scpi", DriverMaturity.PRODUCTION, POLL_WRITE,
                    testPath("ispf-driver-scpi", "com.ispf.driver.scpi.ScpiDeviceDriverTest"),
                    "ispf-driver-scpi"),
            entry("visa", DriverMaturity.PRODUCTION, POLL_WRITE,
                    testPath("ispf-driver-visa", "com.ispf.driver.visa.VisaDeviceDriverTest"),
                    "ispf-driver-visa"),
            entry("knx-tp", DriverMaturity.PRODUCTION, POLL_WRITE,
                    testPath("ispf-driver-knx-tp", "com.ispf.driver.knxtp.KnxTpDeviceDriverTest"),
                    "ispf-driver-knx-tp"),
            // OT Trust Wave 4 — clean-room edge I/O codecs.
            entry("barcode-scanner", DriverMaturity.PRODUCTION, POLL_WRITE,
                    testPath("ispf-driver-barcode-scanner", "com.ispf.driver.barcodescanner.BarcodeScannerDeviceDriverTest"),
                    "ispf-driver-barcode-scanner"),
            entry("weighbridge", DriverMaturity.PRODUCTION, POLL_WRITE,
                    testPath("ispf-driver-weighbridge", "com.ispf.driver.weighbridge.WeighbridgeDeviceDriverTest"),
                    "ispf-driver-weighbridge"),
            entry("weather-station", DriverMaturity.PRODUCTION, POLL_ONLY,
                    testPath("ispf-driver-weather-station", "com.ispf.driver.weatherstation.WeatherStationDeviceDriverTest"),
                    "ispf-driver-weather-station"),
            entry("delta-dvp", DriverMaturity.PRODUCTION, POLL_WRITE,
                    testPath("ispf-driver-delta-dvp", "com.ispf.driver.deltadvp.DeltaDvpDeviceDriverTest"),
                    "ispf-driver-delta-dvp"),
            entry("ls-xgt", DriverMaturity.PRODUCTION, POLL_WRITE,
                    testPath("ispf-driver-ls-xgt", "com.ispf.driver.lsxgt.LsXgtDeviceDriverTest"),
                    "ispf-driver-ls-xgt")
    );

    /** Protocol catalog stubs from {@code ispf-driver-protocol-stubs} (BL protocol-stub pack). */
    private static final Set<String> PROTOCOL_STUB_IDS = loadProtocolStubIds();

    private DriverProductionMatrix() {
    }

    static DriverMaturity resolveMaturity(String driverId) {
        if (PROTOCOL_STUB_IDS.contains(driverId)) {
            return DriverMaturity.STUB;
        }
        Entry entry = ENTRIES.get(driverId);
        if (entry != null) {
            return entry.maturity();
        }
        return DriverMaturity.BETA;
    }

    static Set<String> protocolStubIds() {
        return PROTOCOL_STUB_IDS;
    }

    private static Set<String> loadProtocolStubIds() {
        try (var input = DriverProductionMatrix.class.getResourceAsStream("/driver-pack/protocol-stub-ids.json")) {
            if (input == null) {
                return Set.of();
            }
            // Lightweight parse without pulling Jackson into this static init path.
            String json = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            int start = json.indexOf('[');
            int end = json.indexOf(']', start);
            if (start < 0 || end < 0) {
                return Set.of();
            }
            String body = json.substring(start + 1, end);
            java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
            for (String part : body.split(",")) {
                String token = part.trim();
                if (token.length() >= 2 && token.charAt(0) == '"' && token.charAt(token.length() - 1) == '"') {
                    ids.add(token.substring(1, token.length() - 1));
                }
            }
            return Set.copyOf(ids);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load protocol-stub-ids.json", ex);
        }
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
