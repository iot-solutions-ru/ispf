## Registered driver catalog (162)

The `maturity` field in `GET /api/v1/drivers`: `PRODUCTION` (default), `BETA`, `STUB`. Labels are set in `DriverMaturityRegistry` on the server and shown in the Web Console when selecting a driver.

The `capabilities` field — string set from `DriverProductionMatrix` (ADR-0022): `read`, `write`, `subscribe`, `discovery`, `observed_at`, `quality`. Example: `opcua` → `read`, `write`, `subscribe`, `discovery`, `observed_at`.

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
| `amqp` | `ispf-driver-amqp` | STUB | Apache-2.0 | AMQP 0-9-1 / 1.0 broker stub |
| `ansi-c12` | `ispf-driver-ansi-c12` | STUB | Apache-2.0 | ANSI C12.18/C12.22 meter stub |
| `application` | `ispf-driver-application` | PRODUCTION | Apache-2.0 | Local shell/script execution mapped to variables |
| `as-interface` | `ispf-driver-as-interface` | STUB | Apache-2.0 | AS-Interface master/gateway stub |
| `asterisk` | `ispf-driver-asterisk` | PRODUCTION | Apache-2.0 | Asterisk Manager Interface (AMI) commands |
| `aws-iot-core` | `ispf-driver-aws-iot-core` | STUB | Apache-2.0 | AWS IoT Core MQTT/HTTP stub |
| `azure-iot-hub` | `ispf-driver-azure-iot-hub` | STUB | Apache-2.0 | Azure IoT Hub device/service stub |
| `bacnet` | `ispf-driver-bacnet` | PRODUCTION | Apache-2.0 | BACnet/IP client (clean-room codec) |
| `bacnet-mstp` | `ispf-driver-bacnet-mstp` | STUB | Apache-2.0 | BACnet MS/TP serial stub (BACnet/IP pack is separate) |
| `barcode-scanner` | `ispf-driver-barcode-scanner` | STUB | Apache-2.0 | Barcode/QR TCP/serial scanner stub |
| `beckhoff-ads` | `ispf-driver-beckhoff-ads` | STUB | Apache-2.0 | Beckhoff TwinCAT ADS/AMS stub |
| `bluetooth-le` | `ispf-driver-bluetooth-le` | STUB | Apache-2.0 | Bluetooth Low Energy gateway stub |
| `camera-ai` | `ispf-driver-camera-ai` | STUB | Apache-2.0 | Edge vision/AI inference endpoint stub |
| `canbus-gateway` | `ispf-driver-canbus-gateway` | STUB | Apache-2.0 | Generic CAN/CAN-FD TCP gateway stub |
| `canopen` | `ispf-driver-canopen` | STUB | Apache-2.0 | CANopen / CAN gateway stub |
| `cc-link` | `ispf-driver-cc-link` | STUB | Apache-2.0 | Mitsubishi CC-Link field network stub |
| `cc-link-ie` | `ispf-driver-cc-link-ie` | STUB | Apache-2.0 | Mitsubishi CC-Link IE Field/Control stub |
| `coap` | `ispf-driver-coap` | PRODUCTION | Apache-2.0 | CoAP GET client (read-only) |
| `codesys` | `ispf-driver-codesys` | STUB | Apache-2.0 | CODESYS gateway / PLCHandler stub |
| `controlnet` | `ispf-driver-controlnet` | STUB | Apache-2.0 | ODVA ControlNet gateway stub |
| `corba` | `ispf-driver-corba` | BETA | Apache-2.0 | CORBA IIOP TCP reachability shell (no ORB in modern JDK) |
| `cwmp` | `ispf-driver-cwmp` | PRODUCTION | Apache-2.0 | TR-069/CWMP Inform + Get/SetParameterValues |
| `dali` | `ispf-driver-dali` | STUB | Apache-2.0 | DALI lighting gateway stub |
| `delta-dvp` | `ispf-driver-delta-dvp` | STUB | Apache-2.0 | Delta DVP / AS series PLC stub |
| `device-net` | `ispf-driver-device-net` | STUB | Apache-2.0 | ODVA DeviceNet gateway stub |
| `dhcp` | `ispf-driver-dhcp` | PRODUCTION | Apache-2.0 | DHCP discover probe |
| `dlms` | `ispf-driver-dlms` | PRODUCTION | Apache-2.0 | DLMS/COSEM meter master (TCP WRAPPER) |
| `dnp3` | `ispf-driver-dnp3` | PRODUCTION | Apache-2.0 | DNP3 TCP master — class poll/read (write not implemented) |
| `eebus` | `ispf-driver-eebus` | STUB | Apache-2.0 | EEBUS / SHIP energy management stub |
| `email` | `ispf-driver-email` | PRODUCTION | Apache-2.0 | Outbound email via HTTP relay gateway |
| `enocean` | `ispf-driver-enocean` | STUB | Apache-2.0 | EnOcean ESP3 / USB gateway stub |
| `ethercat` | `ispf-driver-ethercat` | STUB | Apache-2.0 | EtherCAT master/gateway stub |
| `ethernet-ip` | `ispf-driver-ethernet-ip` | PRODUCTION | Apache-2.0 | EtherNet/IP CIP UCMM Read/Write Tag (Allen-Bradley class) |
| `ethernet-powerlink` | `ispf-driver-ethernet-powerlink` | STUB | Apache-2.0 | Ethernet POWERLINK stub |
| `fanuc-focas` | `ispf-driver-fanuc-focas` | STUB | Apache-2.0 | Fanuc FOCAS CNC stub |
| `fatek` | `ispf-driver-fatek` | STUB | Apache-2.0 | Fatek FACON protocol stub |
| `file` | `ispf-driver-file` | PRODUCTION | Apache-2.0 | Local file metadata/content poll |
| `flexible` | `ispf-driver-flexible` | PRODUCTION | Apache-2.0 | Flexible TCP/UDP custom framing poller |
| `folder` | `ispf-driver-folder` | PRODUCTION | Apache-2.0 | Local directory listing poll |
| `foundation-fieldbus` | `ispf-driver-foundation-fieldbus` | STUB | Apache-2.0 | Foundation Fieldbus H1/HSE stub |
| `fuji-sph` | `ispf-driver-fuji-sph` | STUB | Apache-2.0 | Fuji Electric SPH / MICREX stub |
| `ge-srtp` | `ispf-driver-ge-srtp` | STUB | Apache-2.0 | Emerson/GE Fanuc SRTP stub |
| `genicam` | `ispf-driver-genicam` | STUB | Apache-2.0 | GenICam / GigE Vision stub |
| `gps-tracker` | `ispf-driver-gps-tracker` | PRODUCTION | Apache-2.0 | GPS/M2M TCP tracker listener |
| `graph-db` | `ispf-driver-graph-db` | PRODUCTION | Apache-2.0 | Graph DB query (Neo4j / Gremlin) |
| `graphql` | `ispf-driver-graphql` | STUB | Apache-2.0 | GraphQL HTTP stub |
| `grpc` | `ispf-driver-grpc` | STUB | Apache-2.0 | Generic gRPC telemetry stub |
| `hart-ip` | `ispf-driver-hart-ip` | STUB | Apache-2.0 | HART-IP (UDP/TCP) stub |
| `hart-serial` | `ispf-driver-hart-serial` | STUB | Apache-2.0 | HART FSK serial/modem stub |
| `haystack` | `ispf-driver-haystack` | PRODUCTION | Apache-2.0 | Project Haystack HTTP JSON client |
| `hitachi-hidic` | `ispf-driver-hitachi-hidic` | STUB | Apache-2.0 | Hitachi HIDIC / EH-150 stub |
| `http` | `ispf-driver-http` | PRODUCTION | Apache-2.0 | HTTP/HTTPS client poll (GET/POST JSON/text) |
| `http-server` | `ispf-driver-http-server` | PRODUCTION | Apache-2.0 | Embedded HTTP server endpoint for inbound requests |
| `icmp` | `ispf-driver-icmp` | PRODUCTION | Apache-2.0 | ICMP ping reachability / RTT probe |
| `idec-microsmart` | `ispf-driver-idec-microsmart` | STUB | Apache-2.0 | IDEC MicroSmart FC6A stub |
| `iec101` | `ispf-driver-iec101` | STUB | Apache-2.0 | IEC 60870-5-101 serial/TCP stub |
| `iec103` | `ispf-driver-iec103` | STUB | Apache-2.0 | IEC 60870-5-103 protection stub |
| `iec104` | `ispf-driver-iec104` | PRODUCTION | Apache-2.0 | IEC 60870-5-104 client (telecontrol) |
| `iec104-server` | `ispf-driver-iec104-server` | PRODUCTION | Apache-2.0 | IEC 60870-5-104 server/slave |
| `iec61850` | `ispf-driver-iec61850` | STUB | Apache-2.0 | IEC 61850 MMS client stub |
| `iec61850-goose` | `ispf-driver-iec61850-goose` | STUB | Apache-2.0 | IEC 61850 GOOSE subscriber stub |
| `iec61850-sv` | `ispf-driver-iec61850-sv` | STUB | Apache-2.0 | IEC 61850 Sampled Values stub |
| `iec62056` | `ispf-driver-iec62056` | STUB | Apache-2.0 | IEC 62056 DLMS companion / push stub (beyond existing DLMS pack) |
| `ieee2030-5` | `ispf-driver-ieee2030-5` | STUB | Apache-2.0 | IEEE 2030.5 (SEP2) stub |
| `imap` | `ispf-driver-imap` | PRODUCTION | Apache-2.0 | IMAP mailbox poll |
| `ingress-sflow` | `ispf-driver-ingress-sflow` | PRODUCTION | Apache-2.0 | sFlow v5 UDP listener (raw capture ingress) |
| `ingress-snmp-trap` | `ispf-driver-ingress-snmp-trap` | PRODUCTION | Apache-2.0 | SNMP trap UDP listener (raw capture ingress) |
| `ingress-syslog` | `ispf-driver-ingress-syslog` | PRODUCTION | Apache-2.0 | Syslog UDP listener (raw capture ingress) |
| `interbus` | `ispf-driver-interbus` | STUB | Apache-2.0 | INTERBUS fieldbus gateway stub |
| `io-link` | `ispf-driver-io-link` | STUB | Apache-2.0 | IO-Link master REST/MQTT bridge stub |
| `ip-host` | `ispf-driver-ip-host` | PRODUCTION | Apache-2.0 | Multi-check host probe (PING/HTTP/TCP/DNS/SMTP/FTP) |
| `ipmi` | `ispf-driver-ipmi` | PRODUCTION | Apache-2.0 | IPMI LAN BMC probe |
| `isa100` | `ispf-driver-isa100` | STUB | Apache-2.0 | ISA100 wireless gateway stub |
| `j1939` | `ispf-driver-j1939` | STUB | Apache-2.0 | SAE J1939 vehicle network stub |
| `jdbc` | `ispf-driver-jdbc` | PRODUCTION | Apache-2.0 | SQL JDBC SELECT poll |
| `jms` | `ispf-driver-jms` | PRODUCTION | Apache-2.0 | JMS client (ActiveMQ-class) |
| `jmx` | `ispf-driver-jmx` | PRODUCTION | Apache-2.0 | JMX local/remote MBean attribute poll |
| `kafka` | `ispf-driver-kafka` | PRODUCTION | Apache-2.0 | Apache Kafka consumer/poll |
| `keyence-hostlink` | `ispf-driver-keyence-hostlink` | STUB | Apache-2.0 | Keyence PLC Host Link / KV stub |
| `knx` | `ispf-driver-knx` | STUB | Apache-2.0 | KNX/IP tunneling/routing stub |
| `knx-tp` | `ispf-driver-knx-tp` | STUB | Apache-2.0 | KNX Twisted Pair interface stub |
| `ldap` | `ispf-driver-ldap` | PRODUCTION | Apache-2.0 | LDAP search probe |
| `lonworks` | `ispf-driver-lonworks` | STUB | Apache-2.0 | LonWorks/LonTalk IP stub |
| `lorawan` | `ispf-driver-lorawan` | STUB | Apache-2.0 | LoRaWAN network/application server gateway stub |
| `ls-xgt` | `ispf-driver-ls-xgt` | STUB | Apache-2.0 | LS Electric XGT FEnet stub |
| `lwm2m` | `ispf-driver-lwm2m` | STUB | Apache-2.0 | OMA LwM2M client/server stub |
| `matter` | `ispf-driver-matter` | STUB | Apache-2.0 | Matter / CHIP controller stub |
| `mbus` | `ispf-driver-mbus` | PRODUCTION | Apache-2.0 | M-Bus meter protocol (read-only v0.1) |
| `message-stream` | `ispf-driver-message-stream` | PRODUCTION | Apache-2.0 | Generic TCP/UDP message stream framing |
| `mitsubishi-melsec` | `ispf-driver-mitsubishi-melsec` | STUB | Apache-2.0 | Mitsubishi MELSEC communication stub (MC Protocol / SLMP path planned) |
| `mitsubishi-slmp` | `ispf-driver-mitsubishi-slmp` | STUB | Apache-2.0 | Mitsubishi SLMP (Seamless Message Protocol) stub |
| `modbus-rtu` | `ispf-driver-modbus-rtu` | PRODUCTION | Apache-2.0 | Modbus RTU master over serial |
| `modbus-tcp` | `ispf-driver-modbus` | PRODUCTION | Apache-2.0 | Modbus TCP master (FC read/write holding/input/coils) |
| `modbus-udp` | `ispf-driver-modbus-udp` | PRODUCTION | Apache-2.0 | Modbus UDP master |
| `modem-at` | `ispf-driver-modem-at` | PRODUCTION | Apache-2.0 | GSM/cellular modem AT commands over TCP/serial |
| `mqtt` | `ispf-driver-mqtt` | PRODUCTION | Apache-2.0 | MQTT client: subscribe topics and optional publish/write |
| `mqtt-sn` | `ispf-driver-mqtt-sn` | STUB | Apache-2.0 | MQTT For Sensor Networks stub |
| `mtconnect` | `ispf-driver-mtconnect` | STUB | Apache-2.0 | MTConnect agent HTTP stub |
| `nats` | `ispf-driver-nats` | STUB | Apache-2.0 | NATS messaging stub (cluster messaging is separate) |
| `nmea` | `ispf-driver-nmea` | PRODUCTION | Apache-2.0 | NMEA 0183 GNSS/sensor sentence parse |
| `ocpp` | `ispf-driver-ocpp` | STUB | Apache-2.0 | Open Charge Point Protocol (CSMS) stub |
| `odata` | `ispf-driver-odata` | STUB | Apache-2.0 | OData v4 REST stub |
| `odbc` | `ispf-driver-odbc` | PRODUCTION | Apache-2.0 | ODBC via external JDBC bridge JAR (SQL read) |
| `omron-fins` | `ispf-driver-omron-fins` | PRODUCTION | Apache-2.0 | Omron FINS PLC (read-only v0.1) |
| `onvif` | `ispf-driver-onvif` | STUB | Apache-2.0 | ONVIF Profile S/T device stub |
| `opc-ae` | `ispf-driver-opc-ae` | STUB | Apache-2.0 | OPC Classic A&E stub (DCOM/bridge required) |
| `opc-bridge` | `ispf-driver-opc-bridge` | BETA | Apache-2.0 | OPC/LON TCP bridge connectivity shell |
| `opc-da` | `ispf-driver-opc-da` | BETA | Apache-2.0 | OPC Classic DA connectivity shell (needs Windows DCOM/bridge) |
| `opc-hda` | `ispf-driver-opc-hda` | STUB | Apache-2.0 | OPC Classic HDA stub (DCOM/bridge required) |
| `opcua` | `ispf-driver-opcua` | PRODUCTION | Apache-2.0 | OPC UA client (Eclipse Milo): poll/subscribe/write/browse |
| `opcua-pubsub` | `ispf-driver-opcua-pubsub` | STUB | Apache-2.0 | OPC UA PubSub (UDP/MQTT) stub — connectivity shell only |
| `opcua-server` | `ispf-driver-opcua-server` | PRODUCTION | Apache-2.0 | OPC UA server (Eclipse Milo) exposing ISPF variables |
| `openadr` | `ispf-driver-openadr` | STUB | Apache-2.0 | OpenADR 2.0b VTN/VEN stub |
| `panasonic-mewto` | `ispf-driver-panasonic-mewto` | STUB | Apache-2.0 | Panasonic MEWTOCOL-COM/DAT stub |
| `plcnext` | `ispf-driver-plcnext` | STUB | Apache-2.0 | Phoenix Contact PLCnext Engineer/RSC stub |
| `pop3` | `ispf-driver-pop3` | PRODUCTION | Apache-2.0 | POP3 mailbox poll |
| `profibus` | `ispf-driver-profibus` | STUB | Apache-2.0 | PROFIBUS DP/PA gateway stub (serial/fieldbus bridge required) |
| `profibus-pa` | `ispf-driver-profibus-pa` | STUB | Apache-2.0 | PROFIBUS PA instrument network stub |
| `profinet` | `ispf-driver-profinet` | STUB | Apache-2.0 | PROFINET IO controller/device stub (DCP/RPC not implemented) |
| `pulsar` | `ispf-driver-pulsar` | STUB | Apache-2.0 | Apache Pulsar client stub |
| `radius` | `ispf-driver-radius` | PRODUCTION | Apache-2.0 | RADIUS authentication check |
| `redis` | `ispf-driver-redis` | STUB | Apache-2.0 | Redis key/stream telemetry stub |
| `rockwell-csp` | `ispf-driver-rockwell-csp` | STUB | Apache-2.0 | Allen-Bradley CSP (legacy Ethernet) stub |
| `rockwell-df1` | `ispf-driver-rockwell-df1` | STUB | Apache-2.0 | Allen-Bradley DF1 serial/TCP bridge stub |
| `rtsp` | `ispf-driver-rtsp` | STUB | Apache-2.0 | RTSP media/metadata stub |
| `s7` | `ispf-driver-s7` | PRODUCTION | Apache-2.0 | Siemens S7 ISO-on-TCP PLC read/write |
| `schneider-umac` | `ispf-driver-schneider-umac` | STUB | Apache-2.0 | Schneider Electric Unity/Modicon advanced services stub (beyond Modbus) |
| `scpi` | `ispf-driver-scpi` | STUB | Apache-2.0 | IEEE 488.2 SCPI instrument stub |
| `secs-gem` | `ispf-driver-secs-gem` | STUB | Apache-2.0 | SEMI SECS-I/HSMS/GEM stub |
| `sigfox` | `ispf-driver-sigfox` | STUB | Apache-2.0 | Sigfox backend callback stub |
| `sip` | `ispf-driver-sip` | PRODUCTION | LicenseRef-NIST-PublicDomain | SIP OPTIONS/REGISTER reachability probe |
| `smb` | `ispf-driver-smb` | PRODUCTION | Apache-2.0 | SMB/CIFS file share access |
| `smi-s` | `ispf-driver-smis` | PRODUCTION | Apache-2.0 | SMI-S storage CIM-XML poll |
| `smpp` | `ispf-driver-smpp` | PRODUCTION | Apache-2.0 | SMPP SMSC client |
| `sms` | `ispf-driver-sms` | PRODUCTION | Apache-2.0 | Outbound SMS via HTTP relay gateway |
| `snmp` | `ispf-driver-snmp` | PRODUCTION | Apache-2.0 | SNMP v1/v2c/v3 GET/SET poll client |
| `soap` | `ispf-driver-soap` | PRODUCTION | Apache-2.0 | SOAP HTTP client |
| `someip` | `ispf-driver-someip` | STUB | Apache-2.0 | AUTOSAR SOME/IP stub |
| `sparkplug-b` | `ispf-driver-sparkplug-b` | STUB | Apache-2.0 | MQTT Sparkplug B host/edge stub (MQTT session + Sparkplug payload parsing not implemented) |
| `ssh` | `ispf-driver-ssh` | PRODUCTION | Apache-2.0 | SSH remote command execution (JSch) |
| `telnet` | `ispf-driver-telnet` | PRODUCTION | Apache-2.0 | Telnet remote command session |
| `thread` | `ispf-driver-thread` | STUB | Apache-2.0 | Thread Border Router stub |
| `toshiba-t-series` | `ispf-driver-toshiba-t-series` | STUB | Apache-2.0 | Toshiba T-series PLC stub |
| `uds` | `ispf-driver-uds` | STUB | Apache-2.0 | Unified Diagnostic Services over DoIP stub |
| `unitronics` | `ispf-driver-unitronics` | STUB | Apache-2.0 | Unitronics PCOM stub |
| `virtual` | `ispf-driver-virtual` | PRODUCTION | Apache-2.0 | Simulator / virtual device profiles for demos and tests |
| `visa` | `ispf-driver-visa` | STUB | Apache-2.0 | IVI/VISA instrument resource stub |
| `vmware` | `ispf-driver-vmware` | PRODUCTION | Apache-2.0 | VMware vSphere SOAP (Login + RetrieveProperties) |
| `wago` | `ispf-driver-wago` | STUB | Apache-2.0 | WAGO PFC / e!COCKPIT stub |
| `weather-station` | `ispf-driver-weather-station` | STUB | Apache-2.0 | Davis/Vaisala-class weather station stub |
| `web-transaction` | `ispf-driver-web-transaction` | PRODUCTION | Apache-2.0 | Multi-step HTTP transaction script |
| `webhook` | `ispf-driver-webhook` | PRODUCTION | Apache-2.0 | Outbound webhook POST JSON notifications |
| `websocket` | `ispf-driver-websocket` | STUB | Apache-2.0 | Generic WebSocket telemetry stub |
| `weighbridge` | `ispf-driver-weighbridge` | STUB | Apache-2.0 | Truck scale / weighbridge protocol stub |
| `wirelesshart` | `ispf-driver-wirelesshart` | STUB | Apache-2.0 | WirelessHART gateway stub |
| `wisun` | `ispf-driver-wisun` | STUB | Apache-2.0 | Wi-SUN FAN border router stub |
| `wmbus` | `ispf-driver-wmbus` | STUB | Apache-2.0 | Wireless M-Bus (OMS) stub |
| `wmi` | `ispf-driver-wmi` | PRODUCTION | Apache-2.0 | Windows WMI via PowerShell (Windows only) |
| `xmpp` | `ispf-driver-xmpp` | PRODUCTION | Apache-2.0 | XMPP messaging client (Smack) |
| `yaskawa-memobus` | `ispf-driver-yaskawa-memobus` | STUB | Apache-2.0 | Yaskawa Memobus/Modbus-family PLC stub |
| `zigbee` | `ispf-driver-zigbee` | STUB | Apache-2.0 | Zigbee coordinator / ZCL stub |
| `zwave` | `ispf-driver-zwave` | STUB | Apache-2.0 | Z-Wave controller stub |

Detailed configs for base drivers — in the sections below. Others follow the same pattern: `driverConfigJson` + `driverPointMappingsJson`, see `DriverMetadata` in the module.
