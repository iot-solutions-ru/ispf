## Registered driver catalog (163)

The `maturity` field in `GET /api/v1/drivers`: `PRODUCTION` (default), `BETA`, `STUB`. Labels are set in `DriverMaturityRegistry` on the server and shown in the Web Console when selecting a driver.

The `capabilities` field — string set from `DriverProductionMatrix` (ADR-0022): `read`, `write`, `subscribe`, `discovery`, `observed_at`, `quality`. Example: `opcua` → `read`, `write`, `subscribe`, `discovery`, `observed_at`.

`evidence` is `LOOPBACK_VERIFIED` or `FIELD`. `FIELD` is a named plant gate; that set is empty, so every catalog driver is `LOOPBACK_VERIFIED`. `readOnly` is true when `write` is absent. The Web Console shows both next to the maturity badge. `list_drivers` returns the same fields.

### Stub promotion (demand-driven)

58 core `driverId` values are in the production matrix; **97** additional protocol-catalog stubs ship as individual `ispf-driver-<id>` packs (**STUB**, TCP reachability only, **Apache-2.0**). Promotion to **PRODUCTION** is **not** on a roadmap schedule, but **on request from the app team** through the gate [0002-dogfooding-gate](decisions/0002-dogfooding-gate.md):

1. The app team describes the scenario (device, point mapping, acceptance test).
2. A platform PR adds protocol logic (replace the stub class in `ispf-driver-<id>`).
3. `DriverMaturityRegistry` / stub id list is updated; documentation in this file.

Current STUB/BETA candidates:

| `driverId` | Maturity | Note |
|------------|----------|------|
| `corba` | BETA | CORBA IIOP TCP shell — needs a third-party ORB |
| `opc-da`, `opc-bridge` | BETA | Classic OPC shells — prefer `opcua` or external DA→UA |
| Protocol catalog (`sparkplug-b`, `iec61850`, `profinet`, `beckhoff-ads`, `knx`, `lorawan`, …) | STUB | Generated one pack per id from `tools/driver-stubs/protocol-stubs.yaml` (shared base `ispf-driver-stub-kit`) |
| `vmware` | PRODUCTION | vSphere SOAP: Login + RetrieveProperties |
| `smi-s` | PRODUCTION | SMI-S CIM-XML parse |

Loopback tests (BL-26): `EthernetIpDeviceDriverTest`, `OpcDaDeviceDriverTest`, `OpcBridgeDeviceDriverTest`, `CorbaDeviceDriverTest`, `VmwareDeviceDriverTest` (`useHttp`), `SmisDeviceDriverTest` (`useHttp`).

See [ROADMAP.md § Phase 17.4](roadmap.md).

### Complete `driverId` catalog

What each driver does (all packs from `gradle/driver-packs.json`):

| `driverId` | Module | Maturity | License | What it does |
|------------|--------|----------|---------|--------------|
| `amqp` | `ispf-driver-amqp` | PRODUCTION | Apache-2.0 | AMQP 0-9-1 subset (header/start/tune/open/channel/basic.publish+get) — |
| `ansi-c12` | `ispf-driver-ansi-c12` | PRODUCTION | Apache-2.0 | ANSI C12 meter lab over TCP (logon + standard tables); |
| `application` | `ispf-driver-application` | PRODUCTION | Apache-2.0 | Local shell/script execution mapped to variables |
| `as-interface` | `ispf-driver-as-interface` | PRODUCTION | Apache-2.0 | AS-Interface master call over TCP gateway; not AS-i PHY |
| `asterisk` | `ispf-driver-asterisk` | PRODUCTION | Apache-2.0 | Asterisk Manager Interface (AMI) commands |
| `aws-iot-core` | `ispf-driver-aws-iot-core` | BETA | Apache-2.0 | MQTT 3.1.1 client with AWS IoT–shaped topic mappings (not full IoT Core SDK / SigV4) |
| `azure-iot-hub` | `ispf-driver-azure-iot-hub` | BETA | Apache-2.0 | MQTT 3.1.1 client with Azure IoT Hub topic conventions (not full IoT Hub SDK) |
| `bacnet` | `ispf-driver-bacnet` | PRODUCTION | Apache-2.0 | BACnet/IP client (clean-room codec) |
| `bacnet-mstp` | `ispf-driver-bacnet-mstp` | PRODUCTION | Apache-2.0 | BACnet MS/TP clause-9 over TCP serial-server: frame type 5 Read/WriteProperty; |
| `barcode-scanner` | `ispf-driver-barcode-scanner` | PRODUCTION | Apache-2.0 | TCP newline-terminated scan stream: last-scan reads, value+CRLF writes |
| `beckhoff-ads` | `ispf-driver-beckhoff-ads` | PRODUCTION | Apache-2.0 | Reads/writes ADS variables by indexGroup/indexOffset over AMS/TCP (INT/DINT/REAL/STRING) |
| `bluetooth-le` | `ispf-driver-bluetooth-le` | PRODUCTION | Apache-2.0 | H4 HCI commands over TCP; not a BLE radio and not a full GATT client; |
| `camera-ai` | `ispf-driver-camera-ai` | PRODUCTION | Apache-2.0 | HTTP/1.1 client for edge vision/AI inference endpoints (GET read / POST write; no TLS) |
| `canbus-gateway` | `ispf-driver-canbus-gateway` | PRODUCTION | Apache-2.0 | CAN via LAWICEL SLCAN over TCP (standard t frames); |
| `canopen` | `ispf-driver-canopen` | PRODUCTION | Apache-2.0 | CANopen SDO via LAWICEL SLCAN over TCP (standard t frames); |
| `cc-link` | `ispf-driver-cc-link` | PRODUCTION | Apache-2.0 | MELSEC SLMP 3E binary (MC protocol) over TCP for D/R/W devices; |
| `cc-link-ie` | `ispf-driver-cc-link-ie` | PRODUCTION | Apache-2.0 | MELSEC SLMP 3E binary (MC protocol) over TCP for D/R/W via CC-Link IE gateway; |
| `coap` | `ispf-driver-coap` | PRODUCTION | Apache-2.0 | CoAP GET client (read-only) |
| `codesys` | `ispf-driver-codesys` | PRODUCTION | Apache-2.0 | CODESYS-lab text GET/SET gateway on TCP 1217 — not CODESYS network protocol / PLCHandler |
| `controlnet` | `ispf-driver-controlnet` | PRODUCTION | Apache-2.0 | ControlNet CIP Get_Attribute_Single over TCP gateway; not native ControlNet coax |
| `corba` | `ispf-driver-corba` | BETA | Apache-2.0 | CORBA IIOP TCP reachability shell (no ORB in modern JDK) |
| `cwmp` | `ispf-driver-cwmp` | PRODUCTION | Apache-2.0 | TR-069/CWMP Inform + Get/SetParameterValues |
| `dali` | `ispf-driver-dali` | PRODUCTION | Apache-2.0 | IEC 62386 DALI forward frames (two bytes) over TCP; |
| `delta-dvp` | `ispf-driver-delta-dvp` | PRODUCTION | Apache-2.0 | Modbus TCP FC3/FC6 for Delta DVP/AS holding registers |
| `device-net` | `ispf-driver-device-net` | PRODUCTION | Apache-2.0 | DeviceNet CIP Get_Attribute_Single over TCP gateway; not DeviceNet CAN PHY |
| `dhcp` | `ispf-driver-dhcp` | PRODUCTION | Apache-2.0 | DHCP discover probe |
| `dlms` | `ispf-driver-dlms` | PRODUCTION | Apache-2.0 | DLMS/COSEM meter master (TCP WRAPPER) |
| `dnp3` | `ispf-driver-dnp3` | PRODUCTION | Apache-2.0 | DNP3 TCP master — class poll plus CROB latch and g41v3 analog direct operate |
| `eebus` | `ispf-driver-eebus` | PRODUCTION | Apache-2.0 | EEBus SPINE-lite TCP lab (ASCII GET/SET); |
| `email` | `ispf-driver-email` | PRODUCTION | Apache-2.0 | Outbound email via HTTP relay gateway |
| `enocean` | `ispf-driver-enocean` | PRODUCTION | Apache-2.0 | EnOcean ESP3 serial-over-TCP (sync 0x55, CRC8 poly 0x07); |
| `ethercat` | `ispf-driver-ethercat` | PRODUCTION | Apache-2.0 | EtherCAT datagram over TCP (LRD/LWR logical access); |
| `ethernet-ip` | `ispf-driver-ethernet-ip` | PRODUCTION | Apache-2.0 | EtherNet/IP CIP UCMM Read/Write Tag (Allen-Bradley class) |
| `ethernet-powerlink` | `ispf-driver-ethernet-powerlink` | PRODUCTION | Apache-2.0 | POWERLINK frame over TCP gateway; not a hard MN |
| `fanuc-focas` | `ispf-driver-fanuc-focas` | PRODUCTION | Apache-2.0 | FOCAS-shaped TCP gateway lab — length-prefixed ASCII pmc/cnc/stat on 8193; |
| `fatek` | `ispf-driver-fatek` | PRODUCTION | Apache-2.0 | Fatek FACON ASCII command 46/47 register read-write over TCP |
| `file` | `ispf-driver-file` | PRODUCTION | Apache-2.0 | Local file metadata/content poll |
| `flexible` | `ispf-driver-flexible` | PRODUCTION | Apache-2.0 | Flexible TCP/UDP custom framing poller |
| `folder` | `ispf-driver-folder` | PRODUCTION | Apache-2.0 | Local directory listing poll |
| `foundation-fieldbus` | `ispf-driver-foundation-fieldbus` | PRODUCTION | Apache-2.0 | Foundation Fieldbus length-prefixed HSE-style probe over TCP; not an FDA session and not native H1 |
| `fuji-sph` | `ispf-driver-fuji-sph` | PRODUCTION | Apache-2.0 | Fuji SPH/MICREX computer-link ASCII lab (D/M/R) over TCP — not MICREX-SX |
| `ge-srtp` | `ispf-driver-ge-srtp` | PRODUCTION | Apache-2.0 | SRTP-lab MAILBOX read/write for %R/%AI/%AQ/%I/%Q over TCP — not full SRTP |
| `genicam` | `ispf-driver-genicam` | PRODUCTION | Apache-2.0 | GigE Vision GVCP discovery over UDP (control-protocol header starting 42 01 00 02); |
| `gps-tracker` | `ispf-driver-gps-tracker` | PRODUCTION | Apache-2.0 | GPS/M2M TCP tracker listener |
| `graph-db` | `ispf-driver-graph-db` | PRODUCTION | Apache-2.0 | Graph DB query (Neo4j / Gremlin) |
| `graphql` | `ispf-driver-graphql` | PRODUCTION | Apache-2.0 | POSTs GraphQL queries (and optional mutations) over HTTP and maps data fields to points |
| `grpc` | `ispf-driver-grpc` | PRODUCTION | Apache-2.0 | lab HTTP/2 preface and empty SETTINGS; not HPACK and not a protobuf RPC |
| `hart-ip` | `ispf-driver-hart-ip` | PRODUCTION | Apache-2.0 | HART-IP over TCP: session initiate and pass-through short-frame PV read (commands 1 and 3); |
| `hart-serial` | `ispf-driver-hart-serial` | PRODUCTION | Apache-2.0 | HART short-frame over TCP serial gateway: preamble plus command 1/3 PV read; |
| `haystack` | `ispf-driver-haystack` | PRODUCTION | Apache-2.0 | Project Haystack HTTP JSON client |
| `hitachi-hidic` | `ispf-driver-hitachi-hidic` | PRODUCTION | Apache-2.0 | Hitachi HIDIC/EH host-link ASCII lab (WR/R/M) over TCP — not verified HIDIC |
| `http` | `ispf-driver-http` | PRODUCTION | Apache-2.0 | HTTP/HTTPS client poll (GET/POST JSON/text) |
| `http-server` | `ispf-driver-http-server` | PRODUCTION | Apache-2.0 | Embedded HTTP server endpoint for inbound requests |
| `icmp` | `ispf-driver-icmp` | PRODUCTION | Apache-2.0 | ICMP ping reachability / RTT probe |
| `idec-microsmart` | `ispf-driver-idec-microsmart` | PRODUCTION | Apache-2.0 | IDEC MicroSmart Host Link ASCII (D/M/R read-write) over TCP |
| `iec101` | `ispf-driver-iec101` | PRODUCTION | Apache-2.0 | IEC 60870-5-101 unbalanced FT1.2 over TCP: C_IC_NA_1, M_ME_NC_1, M_SP_NA_1, |
| `iec103` | `ispf-driver-iec103` | PRODUCTION | Apache-2.0 | IEC 60870-5-103 FT1.2 over TCP: ASDU 1/2/7/8/9/10/20/40; FUN/INF addressing; |
| `iec104` | `ispf-driver-iec104` | PRODUCTION | Apache-2.0 | IEC 60870-5-104 client (telecontrol) |
| `iec104-server` | `ispf-driver-iec104-server` | PRODUCTION | Apache-2.0 | IEC 60870-5-104 server/slave |
| `iec61850` | `ispf-driver-iec61850` | PRODUCTION | Apache-2.0 | IEC 61850 MMS client over TPKT/COTP (RFC 1006) on TCP 102; |
| `iec61850-goose` | `ispf-driver-iec61850-goose` | PRODUCTION | Apache-2.0 | IEC 61850-8-1 GOOSE header (APPID/Length/Reserved) over UDP; |
| `iec61850-sv` | `ispf-driver-iec61850-sv` | PRODUCTION | Apache-2.0 | IEC 61850-9-2 SAV header (APPID/Length/Reserved) over UDP; |
| `iec62056` | `ispf-driver-iec62056` | PRODUCTION | Apache-2.0 | IEC 62056-21 Mode C ASCII readout over TCP (sign-on /?! , identification, OBIS data lines); |
| `ieee2030-5` | `ispf-driver-ieee2030-5` | PRODUCTION | Apache-2.0 | IEEE 2030.5 (SEP2) HTTP/XML GET subset for EndDeviceList and MeterReading-style resources |
| `imap` | `ispf-driver-imap` | PRODUCTION | Apache-2.0 | IMAP mailbox poll |
| `ingress-sflow` | `ispf-driver-ingress-sflow` | PRODUCTION | Apache-2.0 | sFlow v5 UDP listener (raw capture ingress) |
| `ingress-snmp-trap` | `ispf-driver-ingress-snmp-trap` | PRODUCTION | Apache-2.0 | SNMP trap UDP listener (raw capture ingress) |
| `ingress-syslog` | `ispf-driver-ingress-syslog` | PRODUCTION | Apache-2.0 | Syslog UDP listener (raw capture ingress) |
| `interbus` | `ispf-driver-interbus` | PRODUCTION | Apache-2.0 | INTERBUS process image over TCP; not the Phoenix ASIC |
| `io-link` | `ispf-driver-io-link` | PRODUCTION | Apache-2.0 | IO-Link ISDU over TCP master gateway; not the PHY |
| `ip-host` | `ispf-driver-ip-host` | PRODUCTION | Apache-2.0 | Multi-check host probe (PING/HTTP/TCP/DNS/SMTP/FTP) |
| `ipmi` | `ispf-driver-ipmi` | PRODUCTION | Apache-2.0 | IPMI LAN BMC probe |
| `isa100` | `ispf-driver-isa100` | PRODUCTION | Apache-2.0 | ISA100 gateway ASCII/JSON lab over TCP 4840; |
| `j1939` | `ispf-driver-j1939` | PRODUCTION | Apache-2.0 | SAE J1939 via LAWICEL SLCAN over TCP (extended T frames); |
| `jdbc` | `ispf-driver-jdbc` | PRODUCTION | Apache-2.0 | SQL JDBC SELECT poll |
| `jms` | `ispf-driver-jms` | PRODUCTION | Apache-2.0 | JMS client (ActiveMQ-class) |
| `jmx` | `ispf-driver-jmx` | PRODUCTION | Apache-2.0 | JMX local/remote MBean attribute poll |
| `kafka` | `ispf-driver-kafka` | PRODUCTION | Apache-2.0 | Apache Kafka consumer/poll |
| `keyence-hostlink` | `ispf-driver-keyence-hostlink` | PRODUCTION | Apache-2.0 | Reads/writes Keyence Host Link ASCII registers (RDS/WR) over TCP |
| `knx` | `ispf-driver-knx` | PRODUCTION | Apache-2.0 | KNXnet/IP Tunneling subset: search/connect + GroupValue Read/Write (cEMI) over UDP |
| `knx-tp` | `ispf-driver-knx-tp` | PRODUCTION | Apache-2.0 | KNXnet/IP SEARCH + Tunneling GroupValue Read/Write over UDP |
| `ldap` | `ispf-driver-ldap` | PRODUCTION | Apache-2.0 | LDAP search probe |
| `lonworks` | `ispf-driver-lonworks` | PRODUCTION | Apache-2.0 | LonWorks LonTalk-IP gateway lab (ASCII GET/SET); |
| `lorawan` | `ispf-driver-lorawan` | PRODUCTION | Apache-2.0 | Semtech UDP packet-forwarder network server: PUSH_DATA/PUSH_ACK and PULL_DATA/PULL_ACK |
| `ls-xgt` | `ispf-driver-ls-xgt` | PRODUCTION | Apache-2.0 | XGT dedicated read over TCP (not a certified FEnet stack) |
| `lwm2m` | `ispf-driver-lwm2m` | PRODUCTION | Apache-2.0 | OMA LwM2M resource read via CoAP GET (RFC 7252); |
| `matter` | `ispf-driver-matter` | PRODUCTION | Apache-2.0 | Matter TCP gateway lab (newline JSON attr/cmd on 5540); |
| `mbus` | `ispf-driver-mbus` | PRODUCTION | Apache-2.0 | M-Bus meter protocol (read-only v0.1) |
| `message-stream` | `ispf-driver-message-stream` | PRODUCTION | Apache-2.0 | Generic TCP/UDP message stream framing |
| `mitsubishi-melsec` | `ispf-driver-mitsubishi-melsec` | PRODUCTION | Apache-2.0 | Reads/writes MELSEC D registers via MC Protocol 3E binary (SLMP-compatible) over TCP |
| `mitsubishi-slmp` | `ispf-driver-mitsubishi-slmp` | PRODUCTION | Apache-2.0 | Reads/writes Mitsubishi PLC D registers via SLMP 3E binary over TCP |
| `modbus-rtu` | `ispf-driver-modbus-rtu` | PRODUCTION | Apache-2.0 | Modbus RTU master over serial |
| `modbus-tcp` | `ispf-driver-modbus` | PRODUCTION | Apache-2.0 | Modbus TCP master (FC read/write holding/input/coils) |
| `modbus-udp` | `ispf-driver-modbus-udp` | PRODUCTION | Apache-2.0 | Modbus UDP master |
| `modem-at` | `ispf-driver-modem-at` | PRODUCTION | Apache-2.0 | GSM/cellular modem AT commands over TCP/serial |
| `mqtt` | `ispf-driver-mqtt` | PRODUCTION | Apache-2.0 | MQTT 3.1.1 client (Paho mqttv3): subscribe topics and optional publish/write |
| `mqtt-sn` | `ispf-driver-mqtt-sn` | PRODUCTION | Apache-2.0 | MQTT-SN 1.2 UDP client: CONNECT, REGISTER/SUBSCRIBE, PUBLISH QoS1 |
| `mtconnect` | `ispf-driver-mtconnect` | PRODUCTION | Apache-2.0 | HTTP/1.1 GET of MTConnect Agent /current or /sample XML; extracts data-item values |
| `nats` | `ispf-driver-nats` | PRODUCTION | Apache-2.0 | NATS text protocol client: INFO/CONNECT, SUB for reads, PUB for writes |
| `nmea` | `ispf-driver-nmea` | PRODUCTION | Apache-2.0 | NMEA 0183 GNSS/sensor sentence parse |
| `ocpp` | `ispf-driver-ocpp` | PRODUCTION | Apache-2.0 | OCPP 1.6 JSON Charge Point subset (BootNotification/Heartbeat/StatusNotification) over WebSocket |
| `odata` | `ispf-driver-odata` | PRODUCTION | Apache-2.0 | Polls OData JSON v4 subset endpoints (value array / property) over HTTP GET |
| `odbc` | `ispf-driver-odbc` | PRODUCTION | Apache-2.0 | ODBC via external JDBC bridge JAR (SQL read) |
| `omron-fins` | `ispf-driver-omron-fins` | PRODUCTION | Apache-2.0 | Omron FINS PLC (read-only v0.1) |
| `onvif` | `ispf-driver-onvif` | PRODUCTION | Apache-2.0 | ONVIF Device SOAP subset: GetDeviceInformation, GetCapabilities, Get/SetHostname |
| `opc-ae` | `ispf-driver-opc-ae` | PRODUCTION | Apache-2.0 | HTTP/JSON A&E gateway lab — not OPC Classic DCOM A&E; |
| `opc-bridge` | `ispf-driver-opc-bridge` | BETA | Apache-2.0 | OPC/LON TCP bridge connectivity shell |
| `opc-da` | `ispf-driver-opc-da` | BETA | Apache-2.0 | OPC Classic DA connectivity shell (needs Windows DCOM/bridge) |
| `opc-hda` | `ispf-driver-opc-hda` | PRODUCTION | Apache-2.0 | HDA gateway lab — not OPC Classic DCOM HDA; |
| `opcua` | `ispf-driver-opcua` | PRODUCTION | Apache-2.0 | OPC UA client (Eclipse Milo): poll/subscribe/write/browse |
| `opcua-pubsub` | `ispf-driver-opcua-pubsub` | PRODUCTION | Apache-2.0 | lab UADP version/publisher header; not a full OPC UA PubSub DataSetMessage or security stack |
| `opcua-server` | `ispf-driver-opcua-server` | PRODUCTION | Apache-2.0 | OPC UA server (Eclipse Milo) exposing ISPF variables |
| `openadr` | `ispf-driver-openadr` | PRODUCTION | Apache-2.0 | OpenADR 2.0b oadrPoll VEN over HTTP/1.1 POST (not a full VTN or XML signature stack) |
| `panasonic-mewto` | `ispf-driver-panasonic-mewto` | PRODUCTION | Apache-2.0 | Reads/writes Panasonic MEWTOCOL-COM ASCII D/R registers over TCP |
| `plcnext` | `ispf-driver-plcnext` | PRODUCTION | Apache-2.0 | PLCnext RSC-lab HTTP/JSON symbol read/write on port 41100 |
| `pop3` | `ispf-driver-pop3` | PRODUCTION | Apache-2.0 | POP3 mailbox poll |
| `profibus` | `ispf-driver-profibus` | PRODUCTION | Apache-2.0 | PROFIBUS DP FDL over TCP (serial-server): SD1 station probe, SD2 slave/byte R/W; |
| `profibus-pa` | `ispf-driver-profibus-pa` | PRODUCTION | Apache-2.0 | PROFIBUS PA FDL over TCP (serial-server): SD1/SD2 for slot/addr/pa points; |
| `profinet` | `ispf-driver-profinet` | PRODUCTION | Apache-2.0 | PROFINET DCP Identify over TCP gateway; not PROFINET RT/IRT |
| `pulsar` | `ispf-driver-pulsar` | PRODUCTION | Apache-2.0 | lab binary header plus BaseCommand CONNECT; not a protobuf broker |
| `radius` | `ispf-driver-radius` | PRODUCTION | Apache-2.0 | RADIUS authentication check |
| `redis` | `ispf-driver-redis` | PRODUCTION | Apache-2.0 | Polls Redis keys via RESP GET and writes via SET |
| `rockwell-csp` | `ispf-driver-rockwell-csp` | PRODUCTION | Apache-2.0 | CSP (PCCC-over-Ethernet) lab typed read/write (N/F/B) on TCP 2222 |
| `rockwell-df1` | `ispf-driver-rockwell-df1` | PRODUCTION | Apache-2.0 | DF1 full-duplex protected-mode typed read/write (N/F/B) over TCP serial bridge |
| `rtsp` | `ispf-driver-rtsp` | PRODUCTION | Apache-2.0 | RTSP/1.0 TCP DESCRIBE client with Session/Content-Base parsing |
| `s7` | `ispf-driver-s7` | PRODUCTION | Apache-2.0 | Siemens S7 ISO-on-TCP PLC read/write |
| `schneider-umac` | `ispf-driver-schneider-umac` | PRODUCTION | Apache-2.0 | Modbus TCP FC3/FC6 for Modicon/Unity holding registers (not UMAS/Unity Pro proprietary) |
| `scpi` | `ispf-driver-scpi` | PRODUCTION | Apache-2.0 | Polls IEEE 488.2-style SCPI queries over TCP and writes ASCII set commands |
| `secs-gem` | `ispf-driver-secs-gem` | PRODUCTION | Apache-2.0 | SEMI E37 HSMS over TCP: Select.req/rsp, S1F1/S1F2, S1F13/S1F14, |
| `sigfox` | `ispf-driver-sigfox` | PRODUCTION | Apache-2.0 | Sigfox backend callback over plain HTTP/1.1 GET /uplink (no TLS; not Backend SDK) |
| `sip` | `ispf-driver-sip` | PRODUCTION | LicenseRef-NIST-PublicDomain | SIP OPTIONS/REGISTER reachability probe |
| `smb` | `ispf-driver-smb` | PRODUCTION | Apache-2.0 | SMB/CIFS file share access |
| `smi-s` | `ispf-driver-smis` | PRODUCTION | Apache-2.0 | SMI-S storage CIM-XML poll |
| `smpp` | `ispf-driver-smpp` | PRODUCTION | Apache-2.0 | SMPP SMSC client |
| `sms` | `ispf-driver-sms` | PRODUCTION | Apache-2.0 | Outbound SMS via HTTP relay gateway |
| `snmp` | `ispf-driver-snmp` | PRODUCTION | Apache-2.0 | SNMP v1/v2c/v3 GET/SET poll client |
| `soap` | `ispf-driver-soap` | PRODUCTION | Apache-2.0 | SOAP HTTP client |
| `someip` | `ispf-driver-someip` | PRODUCTION | Apache-2.0 | AUTOSAR SOME/IP over TCP: 16-byte big-endian header, REQUEST/RESPONSE |
| `sparkplug-b` | `ispf-driver-sparkplug-b` | PRODUCTION | Apache-2.0 | Sparkplug B host: MQTT 3.1.1 subscribe NBIRTH/DBIRTH/DDATA, DCMD write, metric name mapping |
| `ssh` | `ispf-driver-ssh` | PRODUCTION | Apache-2.0 | SSH remote command execution (JSch) |
| `stomp` | `ispf-driver-stomp` | PRODUCTION | Apache-2.0 | STOMP 1.2 over TCP or WebSocket. A point is destination or destination\|JSON path |
| `telnet` | `ispf-driver-telnet` | PRODUCTION | Apache-2.0 | Telnet remote command session |
| `thread` | `ispf-driver-thread` | PRODUCTION | Apache-2.0 | OpenThread Spinel HDLC over TCP (CMD_RESET 7E 80 01 02 92 7E); |
| `toshiba-t-series` | `ispf-driver-toshiba-t-series` | PRODUCTION | Apache-2.0 | Toshiba T-series computer-link ASCII lab (D/X/Y) over TCP — not verified Toshiba computer-link |
| `uds` | `ispf-driver-uds` | PRODUCTION | Apache-2.0 | DoIP/UDS subset over TCP (0x10/0x22/0x2E) — not full ISO-TP / ISO 13400 stack |
| `unitronics` | `ispf-driver-unitronics` | PRODUCTION | Apache-2.0 | Unitronics PCOM ASCII (MI/MB read-write) over TCP |
| `virtual` | `ispf-driver-virtual` | PRODUCTION | Apache-2.0 | Simulator / virtual device profiles for demos and tests |
| `visa` | `ispf-driver-visa` | PRODUCTION | Apache-2.0 | SOCKET-only VISA-style resource strings (TCPIP::host::port::SOCKET) for SCPI-over-TCP — not NI-VISA |
| `vmware` | `ispf-driver-vmware` | PRODUCTION | Apache-2.0 | VMware vSphere SOAP (Login + RetrieveProperties) |
| `wago` | `ispf-driver-wago` | PRODUCTION | Apache-2.0 | Modbus TCP FC3/FC6 for WAGO PFC holding registers (not CODESYS proprietary) |
| `weather-station` | `ispf-driver-weather-station` | PRODUCTION | Apache-2.0 | Davis Vantage LOOP command over TCP (LOOP\\n + ACK); not the full 99-byte Vantage packet; |
| `web-transaction` | `ispf-driver-web-transaction` | PRODUCTION | Apache-2.0 | Multi-step HTTP transaction script |
| `webhook` | `ispf-driver-webhook` | PRODUCTION | Apache-2.0 | Outbound webhook POST JSON notifications |
| `websocket` | `ispf-driver-websocket` | PRODUCTION | Apache-2.0 | RFC6455 client. useTls encrypts the socket. Retries connect unless configuration is wrong |
| `weighbridge` | `ispf-driver-weighbridge` | PRODUCTION | Apache-2.0 | TCP MT-SICS level 0 (S/Z/T); not every truck-scale dialect |
| `wirelesshart` | `ispf-driver-wirelesshart` | PRODUCTION | Apache-2.0 | WirelessHART gateway via HART-IP TCP (8-byte header, pass-through); |
| `wisun` | `ispf-driver-wisun` | PRODUCTION | Apache-2.0 | CoAP RFC 7252 CON GET over UDP (port 5683); |
| `wmbus` | `ispf-driver-wmbus` | PRODUCTION | Apache-2.0 | Wireless M-Bus EN 13757-4 Format-A over TCP serial-server: CRC-16/EN-13757, |
| `wmi` | `ispf-driver-wmi` | PRODUCTION | Apache-2.0 | Windows WMI via PowerShell (Windows only) |
| `xmpp` | `ispf-driver-xmpp` | PRODUCTION | Apache-2.0 | XMPP messaging client (Smack) |
| `yaskawa-memobus` | `ispf-driver-yaskawa-memobus` | PRODUCTION | Apache-2.0 | Reads/writes Yaskawa Memobus holding registers via Modbus TCP FC3/FC6 |
| `zigbee` | `ispf-driver-zigbee` | PRODUCTION | Apache-2.0 | ASH (EZSP UART) over TCP; |
| `zwave` | `ispf-driver-zwave` | PRODUCTION | Apache-2.0 | Z-Wave Serial API over TCP (SOF 0x01, ACK 0x06, XOR checksum); |

Detailed configs for base drivers — in the sections below. Others follow the same pattern: `driverConfigJson` + `driverPointMappingsJson`, see `DriverMetadata` in the module.
