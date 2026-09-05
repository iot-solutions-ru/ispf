> **Language:** Canonical English. Russian edition: [ru/driver-promotion.md](../ru/driver-promotion.md).

# Driver promotion process

> **Status:** Stable — PRODUCTION + ready-for-field. Hub: [doc-status.md](doc-status.md).

How to move drivers from **stub** / **beta** to **production** (Phase 3.2).

## Labels

| `maturity` | Meaning |
|------------|----------|
| `PRODUCTION` | Typical scenarios, documented config, tests |
| `BETA` | Working protocol with limitations (platform, auth, partial stack) |
| `STUB` | Connectivity shell — not for production telemetry |

Label is set in `DriverMaturityRegistry` (server) and returned in `GET /api/v1/drivers`.

## Promotion checklist

1. Implement poll/read (or write, when declared) in `ispf-driver-*` module.
2. Add unit/integration tests for point parser and happy path.
3. Update description in `DriverMetadata` and section in [drivers](drivers.md).
4. Change entry in `DriverMaturityRegistry`.
5. If needed — demo device / model in bootstrap.

## Rule: stub / placeholder never PRODUCTION

A driver whose class javadoc documents a **stub** or **placeholder** (connectivity shell, incomplete protocol) **must not** be labeled `PRODUCTION`. CI gate: `DriverProductionMatrixTest.productionDriversMustNotBeDocumentedStubs`. Promote only after real poll/read (and write when claimed) + tests + docs — see checklist below.

## Status (September 2026, OT Trust Wave 2 codec promotion)

| driverId | Was | Now | Note |
|----------|------|-------|------------|
| `redis` | STUB | **PRODUCTION** | RESP GET/SET over TCP; `RedisDeviceDriverTest` (in-process fake RESP server); `POLL` + `WRITE` |
| `mitsubishi-slmp` | STUB | **PRODUCTION** | SLMP 3E binary device-read/write for D registers; `MitsubishiSlmpDeviceDriverTest` (fake SLMP server); points `D100` / `D:100:1`; `POLL` + `WRITE` |
| `yaskawa-memobus` | STUB | **PRODUCTION** | Modbus-TCP FC3/FC6 holding registers; `YaskawaMemobusDeviceDriverTest` (fake Modbus TCP); points `HR:100` / `100`; `POLL` + `WRITE` |
| `sparkplug-b` | STUB | **PRODUCTION** | MQTT (Paho) + minimal Sparkplug B protobuf Payload/Metric codec; host subscribe NBIRTH/DBIRTH/DDATA; DCMD write; `SparkplugBDeviceDriverTest` (Moquette); `POLL` + `WRITE` |






## Status (September 2026, OT Trust Wave 5 start)

| driverId | was | now | notes |
|----------|-----|-----|-------|
| `camera-ai` | STUB | **PRODUCTION** | HTTP/1.1 inference lab GET/POST |
| `dali` | STUB | **PRODUCTION** | DALI gateway ASCII QUERY/SET lab |
| `canbus-gateway` | STUB | **PRODUCTION** | CAN TCP gateway GET/TX ASCII lab |
| `j1939` | STUB | **PRODUCTION** | J1939-over-TCP gateway lab |
| `codesys` | STUB | **PRODUCTION** | CODESYS text gateway lab |

## Status (September 2026, OT Trust Wave 4 batch)

| driverId | was | now | notes |
|----------|-----|-----|-------|
| `keyence-hostlink` | STUB | **PRODUCTION** | Keyence Host Link ASCII lab |
| `panasonic-mewto` | STUB | **PRODUCTION** | MEWTOCOL-COM ASCII lab |
| `fatek` | STUB | **PRODUCTION** | Fatek FACON ASCII lab |
| `azure-iot-hub` | STUB | **PRODUCTION** | MQTT 3.1.1 lab + Azure topic conventions (not Azure SDK) |
| `aws-iot-core` | STUB | **PRODUCTION** | MQTT 3.1.1 lab + AWS topic conventions (not AWS SDK) |
| `iec101` | STUB | **PRODUCTION** | IEC 60870-5-101 TCP lab subset |
| `ansi-c12` | STUB | **PRODUCTION** | ANSI C12.18/22 table-read lab subset |
| `rtsp` | STUB | **PRODUCTION** | RTSP OPTIONS/DESCRIBE/TEARDOWN lab |
| `amqp` | STUB | **PRODUCTION** | AMQP 0-9-1 lab subset (not full broker) |

## Status (September 2026, OT Trust Wave 4 edge)

| driverId | was | now | notes |
|----------|-----|-----|-------|
| `barcode-scanner` | STUB | **PRODUCTION** | TCP newline barcode/QR last-scan + TRIGGER/BEEP |
| `weighbridge` | STUB | **PRODUCTION** | ASCII `W` poll / ZERO/TARE lab dialect |
| `weather-station` | STUB | **PRODUCTION** | `GET FIELD` / `GET ALL` text lab (read-only) |
| `delta-dvp` | STUB | **PRODUCTION** | Modbus-TCP FC3/FC6 lab for Delta DVP/AS |
| `ls-xgt` | STUB | **PRODUCTION** | XGT-lab binary (LSIS-XGT header subset; not certified FEnet) |

**License policy:** Apache-2.0 clean-room / JDK sockets only. Lab readiness ≠ field certification.

## Status (September 2026, OT Trust Wave 3b)

| driverId | Was | Now | Note |
|----------|------|-------|------|
| `ocpp` | STUB | **PRODUCTION** | OCPP 1.6 JSON-lines TCP lab subset |
| `odata` | STUB | **PRODUCTION** | OData JSON v4 HTTP subset |
| `grpc` | STUB | **PRODUCTION** | Honest gRPC-JSON lab (not wire gRPC) |
| `openadr` | STUB | **PRODUCTION** | OpenADR 2.0b VEN poll subset |
| `scpi` | STUB | **PRODUCTION** | SCPI over TCP |
| `visa` | STUB | **PRODUCTION** | SOCKET-only SCPI-over-TCP (not NI-VISA) |
| `knx-tp` | STUB | **PRODUCTION** | KNXnet/IP Routing (“TP via IP”) |

## Status (September 2026, OT Trust Wave 3 clean-room promotion)

| driverId | Was | Now | Note |
|----------|------|-------|------|
| `beckhoff-ads` | STUB | **PRODUCTION** | AMS/TCP AdsRead/AdsWrite IG:IO; loopback fake ADS |
| `mitsubishi-melsec` | STUB | **PRODUCTION** | MC/SLMP 3E D-register R/W; loopback |
| `iec62056` | STUB | **PRODUCTION** | IEC 62056-21 Mode C TCP readout (not DLMS APDU) |
| `ieee2030-5` | STUB | **PRODUCTION** | SEP2 HTTP GET subset |
| `mqtt-sn` | STUB | **PRODUCTION** | MQTT-SN 1.2 UDP CONNECT/PUBLISH/SUBSCRIBE subset |
| `nats` | STUB | **PRODUCTION** | NATS text INFO/CONNECT/SUB/PUB subset |
| `pulsar` | STUB | **PRODUCTION** | Lab TCP text framing (honest non-binary subset) |
| `onvif` | STUB | **PRODUCTION** | Device WSDL GetDeviceInformation subset |
| `mtconnect` | STUB | **PRODUCTION** | Agent HTTP streams poll |
| `knx` | STUB | **PRODUCTION** | KNXnet/IP Tunneling group value R/W |
| `lwm2m` | STUB | **PRODUCTION** | CoAP GET resource read subset |
| `websocket` | STUB | **PRODUCTION** | RFC6455 client text frames |
| `graphql` | STUB | **PRODUCTION** | HTTP GraphQL query/mutation |

**License policy:** Apache-2.0 clean-room / JDK sockets only. Deferred high-risk stacks (`profinet`, `ethercat`, `iec61850*`, proprietary CNC). `visa`/`scpi`/`knx-tp` remain STUB until loopback green.

## Status (July 2026, driver batch C promotion)

| driverId | Was | Now | Note |
|----------|------|-------|------------|
| `ethernet-ip` | BETA | **PRODUCTION** | Real UCMM CIP client: Read/Write Tag (0x4C/0x4D) for BOOL/SINT/INT/DINT/REAL over `SendRRData`; `EthernetIpDeviceDriverTest` (in-test CIP emulator); `POLL` + `WRITE` + `QUALITY` |
| `vmware` | BETA | **PRODUCTION** | Real vSphere SOAP flow: RetrieveServiceContent + SessionManager Login (session cookie) + PropertyCollector RetrieveProperties, re-login on NotAuthenticated, Logout on disconnect; `VmwareDeviceDriverTest` rewritten around a session-enforcing fake endpoint |
| `smi-s` | BETA | **PRODUCTION** | Real CIM-XML parser (JDK DOM/XPath, secure processing) replaces hardcoded properties; CIM `ERROR` handling; `SmisDeviceDriverTest` extended (values, arrays, error, refused) |
| `opc-da`, `opc-bridge`, `corba` | BETA | **BETA** (stays) | Objective blockers: opc-da/opc-bridge proxy protocol undefined (Windows DCOM bridge out of scope); corba needs a third-party ORB (JDK CORBA removed) |

## Status (July 2026, driver batch B3 promotion)

| driverId | Was | Now | Note |
|----------|------|-------|------------|
| `iec104-server` | BETA | **PRODUCTION** | `Iec104ServerDeviceDriverTest` (j60870 client end-to-end); `POLL` + `WRITE` + `QUALITY` |
| `omron-fins` | — (new) | **PRODUCTION** | `OmronFinsDeviceDriverTest` (fake FINS/TCP server: handshake + memory read); read-only |
| `mbus` | — (new) | **PRODUCTION** | `MbusDeviceDriverTest` (fake M-Bus TCP meter, RSP_UD frames); read-only |
| `smpp` | — (new) | **PRODUCTION** | `SmppDeviceDriverTest` (fake SMSC: bind + submit_sm); **fix**: submit_sm source/destination were swapped (source = `systemId`, destination = point destination) |
| `xmpp` | — (new) | **PRODUCTION** | `XmppDeviceDriverTest` (in-test XMPP server: SCRAM-SHA-1 + ping end-to-end); **fix**: `smack-xmlparser-xpp3` / `smack-java8` promoted to runtime deps (`ExceptionInInitializerError` without them) |
| `ipmi` | — (new) | **PRODUCTION** | `IpmiDeviceDriverTest` (RMCP ping + codec seam); **fix**: `readSensor` now issues a real `Get Sensor Reading` |
| `wmi` | BETA | **PRODUCTION** | `WmiDeviceDriverTest` (happy-path gated on Windows); read-only, Windows-only |
| `odbc` | BETA | **PRODUCTION** | `OdbcDeviceDriverTest` (H2 in bridge-compatibility mode — not a real ODBC bridge); requires an external ODBC-JDBC bridge JAR |

## Status (July 2026, driver batch B2 promotion)

| driverId | Was | Now | Note |
|----------|------|-------|------------|
| `sip` | BETA | **PRODUCTION** | `SipDeviceDriverTest` (UDP SIP responder); **fix**: OPTIONS was dead code (jain-sip-ri rejects port-0 ListeningPoint — raw socket now supplies the Via port); `reload4j` moved to runtime deps |
| `asterisk` | BETA | **PRODUCTION** | `AsteriskDeviceDriverTest` (in-test AMI server); read-only |
| `radius` | BETA | **PRODUCTION** | `RadiusDeviceDriverTest` (in-process TinyRadius server: Accept/Reject/unreachable); PAP only |
| `ldap` | BETA | **PRODUCTION** | `LdapDeviceDriverTest` (UnboundID InMemoryDirectoryServer); base DN = root DSE |
| `jmx` | BETA | **PRODUCTION** | `JmxDeviceDriverTest` (in-JVM `JMXConnectorServer`); docs corrected (no local mode, `::` mapping) |
| `nmea` | BETA | **PRODUCTION** | `NmeaDeviceDriverTest` (TCP ServerSocket GGA/RMC); docs corrected (TCP only, prefix matching) |
| `message-stream` | BETA | **PRODUCTION** | `MessageStreamDeviceDriverTest`; **fix**: TCP read now blocking with soTimeout (was `InputStream.available()`) |
| `dhcp` | BETA | **PRODUCTION** | `DhcpDeviceDriverTest` (UDP OFFER responder); **fix**: ports/broadcast address injectable (`serverPort`/`listenPort`/`broadcastAddress`, defaults unchanged) |
| `ingress-syslog` | BETA | **PRODUCTION** | `SyslogIngressDeviceDriverTest` extended (datagram → record + stats); **fixes**: `bindAddress` honored, ingress buffer re-created on `connect()`, description no longer promises RFC parsing |
| `ingress-snmp-trap` | BETA | **PRODUCTION** | `SnmpTrapIngressDeviceDriverTest` (new); raw capture scope documented; same bindAddress/buffer fixes |
| `ingress-sflow` | BETA | **PRODUCTION** | `SflowIngressDeviceDriverTest` (new); raw capture scope documented; same bindAddress/buffer fixes |

## Status (July 2026, driver batch B1 promotion)

| driverId | Was | Now | Note |
|----------|------|-------|------------|
| `imap` | BETA | **PRODUCTION** | `ImapDeviceDriverTest` (GreenMail IMAP); read-only |
| `pop3` | BETA | **PRODUCTION** | `Pop3DeviceDriverTest` (GreenMail POP3); read-only |
| `soap` | BETA | **PRODUCTION** | `SoapDeviceDriverTest` (embedded HttpServer); mapping = full envelope |
| `web-transaction` | BETA | **PRODUCTION** | `WebTransactionDeviceDriverTest` (2-step vs embedded HttpServer); no session/assertions between steps |
| `http-server` | BETA | **PRODUCTION** | `HttpServerDeviceDriverTest`; legacy `write` capability removed (was advertised, never implemented) |
| `jdbc` | BETA | **PRODUCTION** | `JdbcDeviceDriverTest` (H2 in-memory); mapping = full SELECT per point |
| `graph-db` | BETA | **PRODUCTION** | `GraphDbDeviceDriverTest` (Gremlin-HTTP loopback); Bolt branch needs live Neo4j |
| `jms` | BETA | **PRODUCTION** | `JmsDeviceDriverTest` (embedded ActiveMQ `vm://` broker); `browseDepth` over-count bug fixed |

## Status (July 2026, driver batch A promotion)

| driverId | Was | Now | Note |
|----------|------|-------|------------|
| `dnp3` | BETA (BL-191) | **PRODUCTION** | Class 0/1/2/3 poll loopback `Dnp3DeviceDriverTest`; `writePoint` still not implemented |
| `haystack` | BETA | **PRODUCTION** | `HaystackDeviceDriverTest` (embedded HttpServer JSON grid); poll/read only |
| `kafka` | BETA | **PRODUCTION** | `KafkaDeviceDriverTest`; poll/read, `writePoint` read-only |
| `coap` | BETA | **PRODUCTION** | `CoapDeviceDriverTest` (in-process Californium server) |
| `icmp` | BETA | **PRODUCTION** | `IcmpDeviceDriverTest` (localhost reachability) |
| `ip-host` | BETA | **PRODUCTION** | `IpHostDeviceDriverTest` (local listeners + DNS/PING loopback) |
| `telnet` | BETA | **PRODUCTION** | `TelnetDeviceDriverTest`; exit code always 0 (protocol limitation) |
| `modem-at` | BETA | **PRODUCTION** | `ModemAtDeviceDriverTest` (TCP AT stub) |
| `ssh` | BETA | **PRODUCTION** | `SshDeviceDriverTest` (embedded Apache MINA SSHD); `StrictHostKeyChecking=no` |
| `file` | BETA | **PRODUCTION** | `FileDeviceDriverTest` (JUnit temp dirs) |
| `folder` | BETA | **PRODUCTION** | `FolderDeviceDriverTest` (JUnit temp dirs) |
| `application` | BETA | **PRODUCTION** | `ApplicationDeviceDriverTest`; `timeoutMs` bounds wait, hanging child killed |

## Status (July 2026, Phase 25 BL-140 / BL-191 honesty)

| driverId | Was | Now | Note |
|----------|------|-------|------------|
| `iec104` | BETA | **PRODUCTION** | Loopback vs `iec104-server`; write commands |
| `dlms` | BETA | **PRODUCTION** | Gurux read/write; auth NONE |
| `dnp3` | PRODUCTION (poll only) | **PRODUCTION** (poll only) | Class 0/1/2/3 poll; **write not implemented** — ADR-0057 keeps PRODUCTION+POLL_ONLY (not BETA) |
| `ethernet-ip` | PRODUCTION | **PRODUCTION** | CIP session + tag read/write — do **not** mark BETA |
| `opc-da` | PRODUCTION (shell) | **BETA** (BL-191) | Connectivity shell + parser tests — not full DA |
| `opc-bridge` | PRODUCTION (shell) | **BETA** (BL-191) | Bridge point mapping + parser tests |

**Policy:** connectivity shells and incomplete stacks stay **BETA** until the protocol is real. Registry PRODUCTION still ≠ ready-for-field — use [Ready-for-field](#ready-for-field-field-pilots) below.

## Status (June 2026)

| driverId | Was | Now | Note |
|----------|------|-------|------------|
| `dnp3` | STUB | **BETA** | Class 0/1/2/3 poll via `io.stepfunc:dnp3`; write not implemented |
| `cwmp` | STUB | **PRODUCTION** | Inform + ACS `GetParameterValues`; TR-069 acceptance tests |
| `flexible` | BETA | **PRODUCTION** | TCP/UDP request/response |
| `gps-tracker` | BETA | **PRODUCTION** | GPS/M2M TCP server |
| `corba` | STUB | **BETA** | IIOP TCP reachability + point parser tests |
| `ethernet-ip` | STUB | **BETA** | CIP session registration + tag path mapping |
| `opc-da` | STUB | **BETA** | DCOM/TCP connectivity shell + parser tests |
| `opc-bridge` | STUB | **BETA** | Bridge point mapping + parser tests; full OPC stack via external bridge |
| `vmware` | STUB | **BETA** | vSphere API point parser + connectivity shell |
| `smi-s` | STUB | **BETA** | SMI-S CIM point parser + connectivity shell |

Remaining stub drivers require native stack or commercial pack — promotion only on specific request ([licensed-driver-packs](licensed-driver-packs.md)).

## Ready-for-field (field pilots)

**Not automatic** when `maturity: PRODUCTION` or lab interop is green. A driver/scenario is **ready for field** only after:

1. **Named field implementation task** — site, protocol, integrator ticket, scope to implement or harden the driver for that deployment.
2. Lab dry-run green for that scenario ([field-pilot-playbook](field-pilot-playbook.md)).
3. **7-day soak** + customer OT sign-off.

Until (1): status is **playbook-ready** only. See BL-140 (Partial) and [quality path Wave 1](roadmap.md#quality-path-to-done).
