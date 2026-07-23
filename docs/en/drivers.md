> **Language:** Canonical English. Russian edition: [ru/drivers.md](../ru/drivers.md).

# Device drivers

> **Status:** Beta — pack catalog is real; maturity varies (see PRODUCTION matrix / [driver-promotion](driver-promotion.md)). Hub: [doc-status.md](doc-status.md).

Candidate catalog for new drivers (roadmap.md): [roadmap](roadmap.md), full list below (REQ-PF-14).

## Driver maturity

![Device inspector — Flow Meter variables](../assets/ispf-device-inspector.png)

| Level | Meaning |
|-------|---------|
| **production** | Full poll/read/write, tests, documented config |
| **beta** | Working connectivity, limited feature set |
| **stub** | TCP/session check or connectivity shell (v0.1) |
| **simulator** | Virtual/profile-based (see PF-09) |

Many REQ-PF-14 catalog entries are marked as stub — see the table below and [driver-promotion](driver-promotion.md).

Production readiness matrix — [0022-driver-production-matrix](decisions/0022-driver-production-matrix.md), `DriverProductionMatrix` + CI gate `DriverProductionMatrixTest`. Interop lab — [driver-interop-lab](driver-interop-lab.md) (BL-141).

### Top-20 industrial (BL-140, Phase 25)

In `DriverProductionMatrix` — **47** drivers at **PRODUCTION** (including `cwmp` outside top-20) and **9** at **BETA**. Top-20 industrial: **16** **PRODUCTION** + **4** **BETA** (`iec104-server`, `ethernet-ip`, `opc-da`, `opc-bridge`). List: `DriverProductionMatrix.TOP_20_INDUSTRIAL`.

> **Honesty (BL-191):** shells and incomplete stacks are **BETA** in the registry — `opc-da` / `opc-bridge` (connectivity shell + parser tests), `ethernet-ip` (CIP session only). Registry **PRODUCTION** still ≠ ready-for-field; promote via [driver-promotion](driver-promotion.md). See [competitive-scorecard](competitive-scorecard.md) OT dimension.

| `driverId` | Maturity (registry) | Notes / interop |
| ---------- | ------------------- | --------------- |
| `virtual`, `mqtt`, `modbus-tcp`, `modbus-rtu`, `modbus-udp` | PRODUCTION | see interop lab |
| `opcua`, `opcua-server`, `snmp`, `bacnet`, `s7`, `http`, `flexible` | PRODUCTION | see interop lab; OPC UA often SecurityPolicy None in lab |
| `iec104`, `dlms`, `gps-tracker` | PRODUCTION | see interop lab |
| `cwmp` | PRODUCTION | outside top-20; Inform + Get/SetParameterValues |
| `dnp3` | PRODUCTION | **Poll/read only** — `writePoint` not implemented |
| `haystack`, `kafka`, `coap` | PRODUCTION | poll-only clients; loopback tests |
| `icmp`, `ip-host`, `telnet`, `ssh`, `modem-at` | PRODUCTION | IT/remote checks; read-only |
| `file`, `folder`, `application` | PRODUCTION | local host monitoring; read-only |
| `imap`, `pop3`, `jms` | PRODUCTION | mail/messaging clients; read-only |
| `soap`, `web-transaction`, `http-server` | PRODUCTION | HTTP-based; read-only |
| `jdbc`, `graph-db` | PRODUCTION | SELECT-only / query read; read-only |
| `sip`, `asterisk`, `radius` | PRODUCTION | telecom probes; read-only |
| `ldap`, `jmx`, `nmea`, `message-stream`, `dhcp` | PRODUCTION | IT/network checks; read-only |
| `ingress-syslog`, `ingress-snmp-trap`, `ingress-sflow` | PRODUCTION | UDP listeners, raw capture; `observed_at` |
| `ethernet-ip` | BETA | CIP session registration; tag path placeholder |
| `opc-da`, `opc-bridge` | BETA | **Shell / mapping tests** — not full DA stack |
| `iec104-server` | BETA | interop partner for `iec104` |

### observedAt (source timestamps, BL-79)

Poll drivers pass `updateVariable(name, value, observedAt)` to the historian ([0020-time-and-timezones](decisions/0020-time-and-timezones.md)):

| Driver id | observedAt | Source |
| --------- | ---------- | ------ |
| virtual | yes | unified poll tick |
| mqtt | yes | JSON `observedAt` / `timestamp` / epoch |
| modbus-tcp/rtu/udp | yes | shared instant on poll tick |
| opcua | yes | OPC UA SourceTime / ServerTime |
| s7 | yes | poll tick |
| snmp | yes | poll tick |
| bacnet | yes | poll tick |

## Architecture

Drivers implement the `DeviceDriver` SPI (`packages/ispf-driver-api`):

```java
public interface DeviceDriver {
    DriverMetadata metadata();
    void initialize(DriverObject driverObject);
    void connect() throws DriverException;
    void disconnect();
    boolean isConnected();
    void readPoints(Map<String, String> pointMappings) throws DriverException;
    void writePoint(String pointId, DataRecord value) throws DriverException;

    interface DriverObject {
        PlatformObject deviceObject();
        void updateVariable(String name, DataRecord value);
        default void updateVariable(String name, DataRecord value, Instant observedAt) { … }
        Optional<DataRecord> getVariable(String name);
        void log(DriverLogLevel level, String message);
        default Map<String, String> configuration() { return Map.of(); }
    }
}
```

**Ingress contract:** hot path `updateVariable` must not write DB/historian/disk — durable storage is async in the server. Full source: [`DeviceDriver.java`](../../packages/ispf-driver-api/src/main/java/com/ispf/driver/DeviceDriver.java). SDK walkthrough: [driver-ddk](driver-ddk.md).

Registration via **driver packs** in `${ISPF_DRIVER_PACKS_DIR}` (`LicensedDriverPackLoader` → `LicensedDriverRegistry` → `DriverCatalog`). Runtime — `DriverRuntimeService`: poll loop at `pollIntervalMs`.

Build packs: `./gradlew syncAllDriverPacks` → `build/driver-packs/<packId>/`. See [licensed-driver-packs](licensed-driver-packs.md).

## Device variables (driver group)

On a `DEVICE` object, variables in the `driver` group appear when **provisioning the driver** (`POST /objects` with `driverId` or `PUT .../drivers/runtime/configure`), not via auto-apply of MIXIN models.

### Auto-start on server boot

By default, configured drivers **start automatically** after `ApplicationReady`:

| Level | Setting | Default |
|-------|---------|---------|
| Global | `ispf.driver.auto-start-on-boot` / `ISPF_DRIVER_AUTO_START_ON_BOOT` (Platform Settings → Drivers) | `true` |
| Per DEVICE | variable `driverAutoStart` (checkbox in driver inspector) | `true` |

Disable one device: set `driverAutoStart=false`. Disable all: global `false` (requires server restart). Stopping a driver at runtime does **not** clear `driverAutoStart` — after reboot it starts again if the preference is on.

Device create: `autoStartDriver` defaults to `true` (start now + keep preference).

`DeviceProvisioningService` → `SystemObjectStructureService.ensureDeviceDriverStructure()` embeds the schema (`driverId`, `driverStatus`, `driverPollIntervalMs`, `driverConfigJson`, `driverPointMappingsJson`, `status`) from a blueprint without writing to the model catalog and without `appliedBlueprintIds`.

Fixture MIXIN model `device-driver-v1` (when `fixtures-enabled`) — for demo/lab and explicit apply; see [0018-fixture-models-and-cel-applicability](decisions/0018-fixture-models-and-cel-applicability.md).

| Variable | Description |
|----------|-------------|
| `driverId` | Driver ID — full list in the table below |
| `driverStatus` | `STOPPED` / `RUNNING` / `ERROR` |
| `driverPollIntervalMs` | Poll interval |
| `driverConfigJson` | Configuration JSON |
| `driverPointMappingsJson` | JSON: `variableName → pointId` (legacy string) or extended object with Haystack metadata (BL-59) |

### Extended point mappings (BL-59)

Legacy format — a string with protocol address per variable:

```json
{
  "temperature": "HOLDING:1:40001",
  "status": "COIL:1:0"
}
```

Extended object adds Haystack tags for export (`GET /api/v1/platform/haystack/export`) without separate variables per point:

```json
{
  "sineWave": {
    "point": "sim",
    "haystackTags": ["point", "sensor", "temp"],
    "unit": "°C",
    "dis": "Sine wave"
  },
  "status": "sim"
}
```

| Field | Aliases | Purpose |
|-------|---------|---------|
| `point` | `address`, `pointId` | Protocol address (same as legacy string) |
| `haystackTags` | `tags` | Marker tags for Haystack export |
| `unit` | — | Unit of measure (`°C`, `kW`, …) |
| `dis` | — | Display name of the point in export |

Runtime poll/write uses only the protocol address; Haystack fields are ignored by the driver but included in semantic export. Variables with `historyEnabled` are always exported; without history — only if the mapping includes Haystack metadata.

**BACnet example** (`analog-value:1:present-value`):

```json
{
  "supplyTemp": {
    "address": "analog-value:1:present-value",
    "haystackTags": ["point", "sensor", "temp", "supply"],
    "unit": "°C",
    "dis": "Supply air temperature"
  }
}
```

**OPC UA example** (`ns=2;s=TagName`):

```json
{
  "chillerKw": {
    "point": "ns=2;s=Chiller/ElectricPower",
    "tags": ["point", "sensor", "power"],
    "unit": "kW",
    "dis": "Chiller electric power"
  }
}
```

Demo: `root.platform.devices.lab-userA-01` (`HaystackBlueprintBootstrap.DEMO_POINT_MAPPINGS`).

Brick export (BL-60): apply `brick-metadata-v1` mixin, set `brickClass` URI on device → `GET /api/v1/platform/brick/export?format=jsonld|turtle`. `brick:hasPoint` from the same point mappings.

## REST Runtime API

```http
POST /api/v1/drivers/runtime/start?devicePath=root.platform.devices.demo-sensor-01
POST /api/v1/drivers/runtime/stop?devicePath=...
POST /api/v1/drivers/runtime/poll?devicePath=...&pointId=<optional>
POST /api/v1/drivers/runtime/write?devicePath=...&pointId=<variableName>
PUT  /api/v1/drivers/runtime/configure?devicePath=...
GET  /api/v1/drivers/runtime/status?devicePath=...
GET  /api/v1/drivers/runtime/browse?devicePath=...&nodeId=<optional>
```

`poll` without `pointId` refreshes all mapped points; with `pointId` — single mapping key only (BL-84).

`write` body — `DataRecord` with a `value` field (number, boolean, or string). `pointId` — key from `driverPointMappingsJson` (variable name).

## Driver packs (not bundled in server JAR)

Each protocol is a separate pack (`ispf-driver-*`). Without installed packs, `GET /api/v1/drivers` is empty.

### virtual (`ispf-driver-virtual`)

Out-of-the-box simulator for stands without hardware. **No profiles** — one poll path writes multi-type telemetry
(`temperature`+quality, waves, meter/flow, geo, tables, binary, booleans, `status`). Amplitudes/period come from
`driverConfigJson`. Domain plants (Mini-TEC, tank-farm, OGP) enrich the object via **mixin blueprints**
(variables + binding rules / functions), not via `driverConfigJson.profile`.

Example config:

```json
{
  "baseTemperature": "22.0",
  "amplitude": "15.0",
  "periodSec": "60",
  "sineAmplitude": "10.0",
  "sawtoothAmplitude": "5.0",
  "litersPerSecond": "120",
  "filling": "true"
}
```

Recommended model: `virtual-unified-v1` (or thinner `virtual-lab-v1` for waves). Agent: `create_virtual_device`.

### mqtt (`ispf-driver-mqtt`)

Eclipse Paho, topic subscription.

Config: `brokerUrl`, `topicPrefix`, `clientId`, credentials.

Point mapping: `variableName → mqttTopicSuffix`.

Loopback test: `MqttDeviceDriverTest` (embedded moquette broker, subscribe + publish write).

### modbus-tcp (`ispf-driver-modbus`)

j2mod, Modbus TCP. Poll/read/write via `readPoints` / `writePoint`.

Point format: `slaveId:registerType:address[:count]`

Register types: `HOLDING`, `INPUT`, `COIL`, `DISCRETE`.

Write (`writePoint`):

| Type | Modbus function | Field in `DataRecord` |
|------|-----------------|----------------------|
| `HOLDING` | Write Single Register (FC6) | `raw` or `value` (number) |
| `COIL` | Write Single Coil (FC5) | `value` (boolean) |
| `INPUT`, `DISCRETE` | — | read-only, error |

Config: `host`, `port`, `timeoutMs`, `pollIntervalMs`.

### modbus-rtu (`ispf-driver-modbus-rtu`)

j2mod, Modbus RTU serial. Same point format and write matrix as `modbus-tcp`.

Write: `HOLDING` (FC6), `COIL` (FC5); `INPUT`/`DISCRETE` read-only.

Config: `serialPort`, `baudRate`, `dataBits`, `stopBits`, `parity`, `timeoutMs`, `pollIntervalMs`.

### snmp (`ispf-driver-snmp`)

SNMP4J, v1/v2c/v3 GET/SET (v3: USM MD5/SHA + DES/AES128).

Point format: `oid`, `oid:VALUE_KIND` (`STRING`, `INTEGER`, …), or `oid:VALUE_KIND:optional` — the last variant does not abort poll when the OID is missing (for example `hrProcessorLoad` on a Windows SNMP agent).

Loopback test: `SnmpDeviceDriverTest` + in-process `SnmpLoopbackAgent` (GET/SET v2c).

Demo `snmp-localhost`: MIB-II + HOST-RESOURCES-MIB + IF-MIB (see model `snmp-agent-v1` and dashboard `snmp-host-monitoring`):

| Variable | OID |
|----------|-----|
| `sysName` | 1.3.6.1.2.1.1.5.0 |
| `sysDescr` | 1.3.6.1.2.1.1.1.0 |
| `sysUpTime` | 1.3.6.1.2.1.1.3.0 |
| `sysLocation` | 1.3.6.1.2.1.1.6.0 |
| `sysContact` | 1.3.6.1.2.1.1.4.0 |
| `hrMemorySize` | 1.3.6.1.2.1.25.2.2.0 |
| `hrSystemProcesses` | 1.3.6.1.2.1.25.1.6.0 |
| `hrSystemNumUsers` | 1.3.6.1.2.1.25.1.5.0 |
| `ifNumber` | 1.3.6.1.2.1.2.1.0 |
| `ifInOctets` | 1.3.6.1.2.1.2.2.1.10.2 (typical Linux NIC index 2) |
| `ifOutOctets` | 1.3.6.1.2.1.2.2.1.16.2 |
| `hrProcessorLoad` | 1.3.6.1.2.1.25.3.3.1.2.196608 (optional — Linux hrDevice index) |

Config v2c:

```json
{
  "host": "127.0.0.1",
  "port": "161",
  "community": "public",
  "version": "2c",
  "timeoutMs": "3000",
  "retries": "1"
}
```

Config v3 (additional fields; defaults `authProtocol: SHA`, `privProtocol: AES` — legacy `MD5`/`DES` remain selectable explicitly):

```json
{
  "version": "3",
  "securityName": "snmpuser",
  "authProtocol": "SHA",
  "authPassphrase": "authpass",
  "privProtocol": "AES",
  "privPassphrase": "privpass"
}
```

### http (`ispf-driver-http`)

HTTP/HTTPS client (Java HttpClient). Polls REST endpoints.

Point mapping: `path`, `GET:path`, `HEAD:path`, full URL, `:json` suffix for a JSON scalar string.

```json
{
  "baseUrl": "http://127.0.0.1:8080",
  "timeoutMs": "5000"
}
```

Example mappings: `{"platformVersion": "GET:/api/v1/info:json"}`

### haystack (`ispf-driver-haystack`)

Project Haystack HTTP JSON client (SkySpark, FIN, Haxall). Poll-only v0.1: batch `read` by ref, connect probe via `about`.

Point mapping: Haystack ref id (`site.equip.supplyTemp` or `@site.equip.supplyTemp`).

```json
{
  "baseUrl": "https://skyspark.example.com",
  "project": "demo",
  "username": "su",
  "password": "secret",
  "timeoutMs": "5000"
}
```

Alternative: `authToken` (Bearer) instead of username/password.

Example mappings:

```json
{
  "supplyTemp": "site.mainAhu.supplyTemp",
  "runStatus": "@site.mainAhu.run"
}
```

Variable: `value` (number), `valueText` (bool/string), `ref`, `unit`, `dis`. Read-only (v0.1).

Loopback test: `HaystackDeviceDriverTest` (embedded `HttpServer` + JSON grid).

Maturity: **production** (poll/read). Out of scope v0.1: `watch`/subscribe, `pointWrite`, `hisRead`, Zinc codec.

### icmp (`ispf-driver-icmp`)

Host reachability (ICMP / `InetAddress.isReachable`).

Point mapping: hostname or IP per variable; empty value — `host` from config.

```json
{
  "host": "127.0.0.1",
  "timeoutMs": "3000"
}
```

Variable receives: `reachable`, `latencyMs`, `host`.

Maturity: **production**. Loopback test: `IcmpDeviceDriverTest` (localhost reachability).

### ssh (`ispf-driver-ssh`)

Remote shell command execution (JSch).

Point mapping: command per variable, for example `uptime`.

```json
{
  "host": "10.0.0.10",
  "port": "22",
  "username": "admin",
  "password": "secret",
  "timeoutMs": "10000"
}
```

Variable: `value` (stdout), `exitCode`, `stderr`.

Maturity: **production**. Loopback test: `SshDeviceDriverTest` (embedded Apache MINA SSHD server). Limitation: `StrictHostKeyChecking=no` — for production hosts pin keys out-of-band (the driver does not verify host keys).

### coap (`ispf-driver-coap`)

CoAP client (Eclipse Californium), GET resources from IoT devices.

Point mapping: path `/sensor/temp` or full `coap://host:5683/...`

```json
{
  "host": "127.0.0.1",
  "port": "5683",
  "timeoutMs": "5000"
}
```

Loopback test: `CoapDeviceDriverTest` (in-process Californium CoAP server).

Maturity: **production** (poll/read; Observe not supported).

## Registered driver catalog (58)

The `maturity` field in `GET /api/v1/drivers`: `PRODUCTION` (default), `BETA`, `STUB`. Labels are set in `DriverMaturityRegistry` on the server and shown in the Web Console when selecting a driver.

The `capabilities` field — string set from `DriverProductionMatrix` (ADR-0022): `read`, `write`, `subscribe`, `discovery`, `observed_at`, `quality`. Example: `opcua` → `read`, `write`, `subscribe`, `discovery`, `observed_at`.

### Stub promotion (demand-driven)

58 `driverId` values are registered; some are **STUB** or **BETA** (connectivity shell without full protocol). Promotion to **PRODUCTION** is **not** on a roadmap schedule, but **on request from the app team** through the gate [0002-dogfooding-gate](decisions/0002-dogfooding-gate.md):

1. The app team describes the scenario (device, point mapping, acceptance test).
2. A platform PR adds protocol logic to the existing `ispf-driver-*` module.
3. `DriverMaturityRegistry` is updated; documentation in this file.

Current STUB/BETA candidates (June 2026):

| `driverId` | Maturity | Note |
|------------|----------|------|
| `corba` | BETA | CORBA IIOP TCP shell |
| `vmware` | BETA | vSphere SOAP stub |
| `smi-s` | BETA | SMI-S CIM-XML stub |

Loopback tests (BL-26): `EthernetIpDeviceDriverTest`, `OpcDaDeviceDriverTest`, `OpcBridgeDeviceDriverTest`, `CorbaDeviceDriverTest`, `VmwareDeviceDriverTest` (`useHttp`), `SmisDeviceDriverTest` (`useHttp`).

Separate tail: native STUB promotion — see § Stub promotion below.

See [ROADMAP.md § Phase 17.4](roadmap.md).

Full list of `driverId` in `DriverCatalog`:

| `driverId` | Module | Purpose |
|------------|--------|---------|
| `virtual` | `ispf-driver-virtual` | Simulator |
| `mqtt` | `ispf-driver-mqtt` | MQTT subscribe |
| `modbus-tcp` | `ispf-driver-modbus` | Modbus TCP |
| `modbus-rtu` | `ispf-driver-modbus-rtu` | Modbus RTU serial |
| `modbus-udp` | `ispf-driver-modbus-udp` | Modbus UDP |
| `snmp` | `ispf-driver-snmp` | SNMP v1/v2c/v3 |
| `http` | `ispf-driver-http` | HTTP/HTTPS client |
| `haystack` | `ispf-driver-haystack` | Project Haystack HTTP JSON client |
| `http-server` | `ispf-driver-http-server` | Embedded HTTP server |
| `icmp` | `ispf-driver-icmp` | Ping |
| `ssh` | `ispf-driver-ssh` | SSH command |
| `coap` | `ispf-driver-coap` | CoAP GET |
| `opcua` | `ispf-driver-opcua` | OPC UA client (Milo) |
| `opcua-server` | `ispf-driver-opcua-server` | OPC UA server (Milo) |
| `opc-da` | `ispf-driver-opc-da` | OPC DA (DCOM/native bridge) |
| `opc-bridge` | `ispf-driver-opc-bridge` | OPC/LON bridge TCP |
| `s7` | `ispf-driver-s7` | Siemens S7 |
| `iec104` | `ispf-driver-iec104` | IEC 104 client |
| `iec104-server` | `ispf-driver-iec104-server` | IEC 104 server/slave |
| `bacnet` | `ispf-driver-bacnet` | BACnet/IP |
| `dnp3` | `ispf-driver-dnp3` | DNP3 TCP master (Class 0/1/2/3 poll) |
| `ethernet-ip` | `ispf-driver-ethernet-ip` | EtherNet/IP CIP session + tag path |
| `dlms` | `ispf-driver-dlms` | DLMS/COSEM master (Gurux read/write) |
| `jmx` | `ispf-driver-jmx` | JMX local/remote |
| `jdbc` | `ispf-driver-jdbc` | SQL JDBC |
| `odbc` | `ispf-driver-odbc` | ODBC via JDBC bridge JAR |
| `file` | `ispf-driver-file` | File metadata/content |
| `folder` | `ispf-driver-folder` | Directory listing |
| `application` | `ispf-driver-application` | Shell/script |
| `message-stream` | `ispf-driver-message-stream` | TCP/UDP stream |
| `nmea` | `ispf-driver-nmea` | NMEA 0183 |
| `telnet` | `ispf-driver-telnet` | Telnet |
| `soap` | `ispf-driver-soap` | SOAP |
| `ip-host` | `ispf-driver-ip-host` | PING, HTTP, TCP, DNS, SMTP, FTP |
| `ldap` | `ispf-driver-ldap` | LDAP search |
| `dhcp` | `ispf-driver-dhcp` | DHCP discover |
| `imap` | `ispf-driver-imap` | IMAP mailbox |
| `pop3` | `ispf-driver-pop3` | POP3 mailbox |
| `radius` | `ispf-driver-radius` | RADIUS auth check |
| `ipmi` | `ispf-driver-ipmi` | IPMI LAN |
| `wmi` | `ispf-driver-wmi` | WMI (PowerShell, Windows) |
| `kafka` | `ispf-driver-kafka` | Kafka |
| `jms` | `ispf-driver-jms` | JMS (ActiveMQ) |
| `cwmp` | `ispf-driver-cwmp` | TR-069 Inform client |
| `web-transaction` | `ispf-driver-web-transaction` | Multi-step HTTP |
| `graph-db` | `ispf-driver-graph-db` | Neo4j / Gremlin |
| `vmware` | `ispf-driver-vmware` | vSphere SOAP stub |
| `smi-s` | `ispf-driver-smis` | SMI-S CIM-XML stub |
| `gps-tracker` | `ispf-driver-gps-tracker` | GPS/M2M TCP server |
| `flexible` | `ispf-driver-flexible` | Flexible TCP/UDP |
| `mbus` | `ispf-driver-mbus` | M-Bus |
| `modem-at` | `ispf-driver-modem-at` | GSM AT commands |
| `omron-fins` | `ispf-driver-omron-fins` | Omron FINS |
| `asterisk` | `ispf-driver-asterisk` | Asterisk AMI |
| `sip` | `ispf-driver-sip` | SIP OPTIONS/REGISTER |
| `xmpp` | `ispf-driver-xmpp` | XMPP (Smack) |
| `smpp` | `ispf-driver-smpp` | SMPP |
| `smb` | `ispf-driver-smb` | SMB/CIFS |
| `corba` | `ispf-driver-corba` | CORBA IIOP TCP stub |
| `ingress-syslog` | `ispf-driver-ingress-syslog` | Syslog UDP listener (raw capture) |
| `ingress-snmp-trap` | `ispf-driver-ingress-snmp-trap` | SNMP trap UDP listener (raw capture) |
| `ingress-sflow` | `ispf-driver-ingress-sflow` | sFlow v5 UDP listener (raw capture) |

Detailed configs for base drivers — in the sections below. Others follow the same pattern: `driverConfigJson` + `driverPointMappingsJson`, see `DriverMetadata` in the module.

### v0.1 limitations (native / full stack required)

| `driverId` | What exists now | For production |
|------------|-----------------|----------------|
| `ethernet-ip` | Register Session | CIP tag read/write library |
| `dlms` | TCP WRAPPER + read/write | Gurux association (auth NONE v0.2) |
| `opc-da` | status / proxy TCP | Windows DCOM bridge |
| `corba` | IIOP TCP | JDK CORBA removed; use bridge |
| `wmi` | PowerShell | Windows only |

### Examples (brief)

### opcua (`ispf-driver-opcua`)

OPC UA client (Eclipse Milo). Poll/read/write via `readPoints` / `writePoint`; optional push via subscriptions.

Point mapping: `ns=2;s=TagName` (NodeId).

Write (`writePoint`): Milo `writeValue` on Value attribute; Variant type is chosen from the current node value (boolean, numeric, string, unsigned). Fields `value` or `raw`.

Config:

| Key | Default | Description |
|-----|---------|-------------|
| `endpointUrl` | `opc.tcp://localhost:4840` | OPC UA endpoint |
| `timeoutMs` | `5000` | Connect/read/write timeout |
| `pollIntervalMs` | `1000` | Scheduler poll interval |
| `readMode` | `poll` | `poll` — synchronous read; `subscribe` — ManagedSubscription push with poll fallback on error |

**Browse / discovery:** `GET /api/v1/drivers/runtime/browse?devicePath=…&nodeId=` (optional). Driver implements `DriverDiscovery`; Web Console inspector — "Browse OPC UA" on connected device.

**Security (v0.2):** production deployments should use Sign/SignAndEncrypt with client certificate and trust store. Current driver connects with **SecurityPolicy None** only (lab/loopback).

Maturity: **production**. Loopback tests: `OpcUaDeviceDriverTest` (browse, write, `readMode=subscribe`).

### s7 (`ispf-driver-s7`)

Siemens S7 over ISO-on-TCP. Poll/read/write via `readPoints` / `writePoint`.

Point mapping: `area:dbNumber:offset:type` (for example `DB:1:0:REAL`).

Supported types: `BOOL`, `BYTE`, `SINT`, `USINT`, `INT`, `UINT`, `WORD`, `DINT`, `UDINT`, `DWORD`, `REAL`, `LREAL`.

Write (`writePoint`):

| Type | Field in `DataRecord` |
|------|-------------------------|
| `BOOL` | `value` (boolean); read-modify-write of bit 0 in byte at offset |
| integer types | `raw` or `value` (number) |
| `REAL`, `LREAL` | `value` or `raw` (number) |

Config: `host`, `port` (102), `rack`, `slot`, `timeoutMs`.

### iec104 (`ispf-driver-iec104`)

IEC 60870-5-104 master. Config: `host`, `port` (2404), `commonAddress`, `timeoutMs`.

Point mapping: `ioa:dataType` (for example `2001:BOOL`, `3001:FLOAT`, `1001:M_ME_NA_1`).

**Write (BL-23):** `BOOL` / `M_SP_NA_1` → `singleCommand`; `FLOAT` / `M_ME_NC_1` → `setShortFloatCommand`; `INT` / `M_ME_NA_1` → `setNormalizedValueCommand`. After write the variable is updated locally (`quality=GOOD`); poll read may return `NOT_AVAILABLE` if the outstation does not respond to read command.

Loopback test: `Iec104DeviceDriverTest` against `iec104-server`.

Maturity: **production** (BL-140).

### bacnet (`ispf-driver-bacnet`)

BACnet/IP read/write property (`present-value`). Config:

| Key | Default | Description |
|-----|---------|-------------|
| `host` | `127.0.0.1` | Remote device IP (required for `discoveryMode=static`) |
| `port` | `47808` | Remote BACnet/IP UDP port |
| `localDeviceId` | `1234` | Local BACnet device instance |
| `remoteDeviceId` | `1001` | Target remote device instance |
| `discoveryMode` | `static` | `static` — use `host`/`port`; `whoIs` — discover via Who-Is/I-Am (host optional on loopback) |
| `timeoutMs` | `5000` | Connect/read timeout |
| `bindAddress` | `0.0.0.0` | Local UDP bind address |
| `bindPort` | same as `port` | Local UDP bind port when different from remote |

Point mapping: `objectType:instance:property` (for example `analog-output:1:present-value`).

**Read output:** `value` (typed string: analog float, binary `active`/`inactive`, multi-state integer), `property`, optional `unit` (Haystack-friendly, from BACnet `units` on analog present-value).

**Write:** `analog-output`/`analog-value` → `Real`; `binary-output`/`binary-value` → `BinaryPV`; `multi-state-output`/`multi-state-value` → `UnsignedInteger`. Read-only: `analog-input`, `binary-input`, `multi-state-input`.

Maturity: **production**. Tests: guard-rails + `BacnetLoopbackServer` Who-Is smoke (`BacnetDeviceDriverTest`); property read/write + discovery — `BacnetDeviceDriverNetworkTest` (bacnet4j in-memory `TestNetwork`, CI-safe). Loopback subnet (`127.0.0.0/8`) auto-selected for `127.0.0.1` / Who-Is mode.

### dnp3 (`ispf-driver-dnp3`)

DNP3 TCP **master** with integrity poll Class 0/1/2/3 (`io.stepfunc:dnp3` 1.6.0).

Config: `host`, `port`, `localAddress` (master link address, default `1`), `outstationAddress` (default `1024`), `timeoutMs`.

Point mapping: `index:dataType` — `BINARY_INPUT`, `BINARY_OUTPUT`, `ANALOG_INPUT`, `ANALOG_OUTPUT`, `COUNTER` (for example `0:ANALOG_INPUT`).

On each `readPoints`, `Request.classRequest(0,1,2,3)` runs; values and DNP3 flags (`status`) are updated on the object variables.

Maturity: **production** in registry for Class 0/1/2/3 **poll/read** (loopback `Dnp3DeviceDriverTest`, BL-140). **`writePoint` is not implemented** — do not plan control/write field pilots on DNP3 until write lands; treat as BETA for write scenarios.

### dlms (`ispf-driver-dlms`)

DLMS/COSEM **master** over TCP **WRAPPER** (`gurux.dlms` + `gurux.net`).

Config: `host`, `port` (default `4059`), `clientAddress` (default `16`), `logicalDevice` (default `1`), `timeoutMs`.

Point mapping: `logicalDevice:obis[:objectType[:attribute]]` — default `REGISTER`, attribute `2`.  
Examples: `1:1.0.1.8.0.255`, `1:0.0.42.0.0.255:DATA:2`.

`readPoints` / `writePoint`: SNRM + AARQ association, Gurux GET/SET. Write: fields `value` or `raw` (numeric for REGISTER).

Maturity: **production** (auth NONE; loopback `DlmsDeviceDriverTest`, BL-140).

### jmx (`ispf-driver-jmx`)

Remote JMX over RMI. Config: `serviceUrl` (`service:jmx:rmi:///jndi/rmi://host:port/jmxrmi`) — or leave empty to build the URL from `host`/`port`. There is no local/platform-MBeanServer mode; `timeoutMs` is declared but currently unused.

Point mapping: `objectName::attribute[.compositeKey]` — for example `java.lang:type=Memory::HeapMemoryUsage.used`. Prefer the `::` separator; the legacy single-colon form mis-parses objectNames (which always contain `:`).

Maturity: **production**. Loopback test: `JmxDeviceDriverTest` (JDK `JMXConnectorServer` + RMI registry in-JVM). Read-only.

### jdbc (`ispf-driver-jdbc`)

SQL scalar/row read over any JDBC driver. Config: `jdbcUrl`, `username`, `password`, `timeoutMs`.

Point mapping: a **full SELECT statement per point** (case-insensitive `SELECT` guard; connection is opened `setReadOnly(true)`). There is no `query` config key.

Variable: single-column result → `value` (first row); multi-column first row → one string field per column label (sanitized `[^a-zA-Z0-9_]` → `_`).

Maturity: **production**. Loopback test: `JdbcDeviceDriverTest` (H2 in-memory). Read-only (`writePoint` throws).

### graph-db (`ispf-driver-graph-db`)

Graph query client: Neo4j Bolt (`bolt://` URI, official neo4j-java-driver) or Gremlin-over-HTTP (`http(s)://` URI), selected by URI scheme. Config: `uri`, `username`, `password`, `timeoutMs`.

Point mapping: query script (Cypher or Gremlin depending on branch). Variable: `value` — Bolt branch: first column of the first record; Gremlin-HTTP branch: the raw response body (parse Gremlin JSON on the caller side).

Maturity: **production** for the Gremlin-HTTP branch (loopback `GraphDbDeviceDriverTest` vs embedded HttpServer); the Bolt branch requires a live Neo4j and is not covered by loopback tests. Read-only.

### file / folder

`file`: point mapping — file path (relative to `basePath` config or absolute). Variable: `exists`, `size`, `lastModified`, `value` (text preview, first 4 KB).  
`folder`: point mapping — directory path. Variable: `exists`, `fileCount`, `totalBytes`.

Maturity: **production**. Loopback tests: `FileDeviceDriverTest`, `FolderDeviceDriverTest` (JUnit temp dirs).

### application (`ispf-driver-application`)

Local process launch (ProcessBuilder; `cmd.exe /c` on Windows, `sh -c` elsewhere).

Config: `workingDir`, `timeoutMs`. **Command is the point mapping**, not a config key — mapping value is the full command line per variable.

Variable: `value` (stdout), `exitCode`, `stderr`.

Maturity: **production**. Loopback test: `ApplicationDeviceDriverTest`. Limitation: `timeoutMs` bounds the wait for process exit; a silently hanging child is killed on timeout but output streamed before the kill is lost.

### message-stream (`ispf-driver-message-stream`)

Raw TCP client or UDP client/listener. Config: `protocol` (`TCP`/`UDP`), `host`, `port`, `listen` (UDP only — TCP listen is explicitly unsupported), `bufferSize`.

Point mapping: mapping values are currently **not interpreted** — each poll reads the stream once and pushes the same record to every mapped point; conventionally map a `feed` point. Variable: `stream` (UTF-8 chunk, empty on timeout), `bytesRead`.

Maturity: **production**. Loopback test: `MessageStreamDeviceDriverTest` (UDP listener + TCP client vs local ServerSocket; blocking read bounded by soTimeout). Read-only.

### nmea (`ispf-driver-nmea`)

NMEA 0183 over **TCP only** (per-poll reconnect, up to 100 lines per poll). Config: `host`, `port` (10110). There is no serial mode — `mode`/`serialPort` keys are not implemented.

Point mapping: sentence-type **prefix**, case-insensitive (`startsWith` on the type token — `GP` matches `GPGGA`, bare `GGA` does not); the last matching line in the poll wins. Variable: `value` (JSON `{"type":…,"f1":…,"fN":…}`, `{}` on no match), `raw` (last matching sentence).

Maturity: **production**. Loopback test: `NmeaDeviceDriverTest` (local ServerSocket streaming GGA/RMC). Read-only.

### telnet / soap

`telnet` (`ispf-driver-telnet`): config `host`, `port`, `username`, `password`, `timeoutMs`; point mapping — shell command per variable. Variable: `value` (output), `exitCode`, `stderr`. Maturity: **production** — loopback `TelnetDeviceDriverTest`. Limitation: exit code is reported as `0` on completed sessions (Telnet has no exit-status channel).  
`soap` (`ispf-driver-soap`): config `endpointUrl`, `soapAction`, `timeoutMs`; point mapping — the **full SOAP envelope XML**, POSTed as-is (`Content-Type: text/xml`; `SOAPAction` header only when configured). Variable: `value` (whole response body), `statusCode`. HTTP 500 is mapped as `statusCode` + fault body, not thrown. Maturity: **production** — loopback `SoapDeviceDriverTest` (embedded HttpServer). Read-only.

### web-transaction (`ispf-driver-web-transaction`)

Multi-step HTTP scenario. Config: `stepsJson` (default steps), `timeoutMs`.

Point mapping: pipe-delimited `name:METHOD:url[:body]` steps or a JSON array `[{"name","method","url","body"}]`; a blank mapping falls back to the `stepsJson` config.

Variable: `statusCode`, `latencyMs`, `value` (final step body). Limitations: no cookies/session between steps, no per-step assertions or extraction, no auth, no per-step headers.

Maturity: **production**. Loopback test: `WebTransactionDeviceDriverTest` (2-step scenario vs embedded HttpServer). Read-only.

### http-server (`ispf-driver-http-server`)

Embedded HTTP server — external systems POST into the platform. Config: `listenPort`, `contextPath`.

Point mapping: `requests` (total count), `lastPath`, `lastBody`. Variable: `value`, `count`. Requests outside `contextPath` get 404 and are not counted.

Maturity: **production**. Loopback test: `HttpServerDeviceDriverTest`. Read-only — the `write` capability previously advertised via the legacy registry was removed (matrix `POLL` only).

### modem-at (`ispf-driver-modem-at`)

GSM modem AT commands over serial port or TCP (RFC2217-style bridge).

Config: `mode` (`tcp`/`serial`), `host`, `port` (TCP mode) or `serialPort`, `baudRate` (serial mode), `timeoutMs`.

Point mapping: AT command per variable (`AT+CSQ`, `AT+COPS?`, …). Variable: `value` (parsed payload), `response` (raw modem answer), `success`.

Maturity: **production**. Loopback test: `ModemAtDeviceDriverTest` (TCP AT stub).

### ip-host (`ispf-driver-ip-host`)

Unified IT monitoring. Config: `defaultHost`, `timeoutMs`. Point mapping prefixes:

| Prefix | Example | Check |
|--------|---------|-------|
| `PING:` | `PING:8.8.8.8` | ICMP |
| `HTTP:` | `HTTP:https://host/` | HTTP HEAD |
| `TCP:` | `TCP:host:443` | TCP connect |
| `DNS:` | `DNS:example.com` | DNS resolve |
| `SMTP:` | `SMTP:host:25` | SMTP banner |
| `FTP:` | `FTP:host:21` | FTP connect |

Maturity: **production**. Loopback test: `IpHostDeviceDriverTest` (local listeners + DNS/PING loopback).

### kafka (`ispf-driver-kafka`)

Config: `bootstrapServers`, `topic`, `groupId`, `timeoutMs`, `eventToVariable`.

Point mapping: `consume` (last message) or `produce:payload`.

Maturity: **production** (poll/read; `writePoint` is read-only — producing is done via `produce:` point mappings). Loopback test: `KafkaDeviceDriverTest`.

### jms (`ispf-driver-jms`)

JMS queue/topic client (ActiveMQ Classic client). Config: `brokerUrl`, `destination`, `destinationType` (`queue`/`topic`), `timeoutMs`.

Point mapping: `consume` (receive with timeout, destructive — AUTO_ACK) or `browse[:depth]` (queue depth, capped scan; queues only — topic browse is rejected).

Variable: `value` (payload / depth-as-string), `depth`.

Maturity: **production**. Loopback test: `JmsDeviceDriverTest` (embedded ActiveMQ `vm://` broker). Read-only (`writePoint` throws).

### imap (`ispf-driver-imap`)

IMAP mailbox monitoring (Jakarta Mail / angus-mail). Config: `host`, `port` (993), `username`, `password`, `folder` (`INBOX`), `useSsl` (`true`).

Point mapping: `messageCount`, `unseen`, `subject:N` (1-based message number). Variable: `value`, `count`.

Maturity: **production**. Loopback test: `ImapDeviceDriverTest` (GreenMail IMAP server). Read-only. Note: the store is re-opened on each poll.

### pop3 (`ispf-driver-pop3`)

POP3 mailbox monitoring (Jakarta Mail / angus-mail). Config: `host`, `port` (110), `username`, `password`.

Point mapping: `stat` (count + total size) or `retr:N` (1-based message number). Variable: `value`, `count`, `sizeBytes`. Note: `retr:N` returns the decoded message content (body), headers are not included.

Maturity: **production**. Loopback test: `Pop3DeviceDriverTest` (GreenMail POP3 server). Read-only.

### cwmp (`ispf-driver-cwmp`) — PRODUCTION

TR-069 CPE client: Periodic Inform to ACS, handles `GetParameterValues` RPC.

Config (`driverConfigJson`):

```json
{
  "acsUrl": "http://acs.example:7547/",
  "deviceId": "000000-000000000000",
  "timeoutMs": "5000",
  "informParameters": "Device.DeviceInfo.SoftwareVersion"
}
```

Point mapping: TR-069 parameter name (for example `Device.DeviceInfo.SoftwareVersion`) or `connected` (status of last Inform).

Write: `POST /api/v1/drivers/runtime/write` with `pointId` and `{ "rows": [{ "value": "..." }] }` — driver applies `SetParameterValues` locally and sends `SetParameterValuesResponse` to ACS. Requires prior poll (point mapping in memory). Pseudo-point `connected` — read-only.

### gps-tracker (`ispf-driver-gps-tracker`)

TCP **server** — devices connect to the platform. Config: `listenPort`, `bufferSize`.

Point mapping: `feed` — last line/buffer.

### flexible (`ispf-driver-flexible`)

Universal TCP/UDP. Version **0.2.0** — legacy mode and **exchange pipeline** for framed request/response.

#### Legacy (unchanged)

Config: `protocol`, `host`, `port`, `encoding` (`hex`|`utf8`|`escapes`), `timeoutMs`.

Point mapping: `request[:responseRegex]` — send and optional regex capture group 1.

#### Exchange pipeline

For ASCII/framed protocols (SOH/ETX, optional checksum, structured extractors). Typical case — **serial computer format over TCP**: ASCII function code + ASCII-hex floats; checksum on the wire is often **not verified** (`checksumAlgorithm: none`), because TCP already provides transport integrity.

Device config:

| Key | Value | Description |
|-----|-------|-------------|
| `readMode` | `idle` (default) \| `delimiter` | `idle` — read while buffer is empty; `delimiter` — until byte `readUntilHex` |
| `readUntilHex` | `03` | terminator (hex), when `readMode=delimiter` |
| `readMaxBytes` | `8192` | response limit |
| `checksumAlgorithm` | `none` \| `sum16-complement-hex` | `none` — typical for serial-over-TCP; `sum16-complement-hex` — when gateway requires verification |
| `checksumMarker` | `&&` | marker before checksum |
| `checksumLength` | `4` | checksum length (hex characters) |
| arbitrary keys | | substitution in `${key}` request templates |

Point mapping (pipeline):

```
req:{template}|var:{k}={v}|verifyChecksum|extract:{type}:{args}
```

| Segment | Example |
|---------|---------|
| `req:` | `\x01${securityCode}i20101` — `\xHH` escapes, `${var}` from config/point |
| `var:` | `tank=01` — per-point variables |
| `verifyChecksum` | explicit verification (or auto when `checksumAlgorithm`) |
| `extract:regex:{pattern}:{group}` | regex capture |
| `extract:asciiHexFloat:{index}` | N-th 8-nibble IEEE float in payload |
| `extract:asciiHexFloat:{index}:after:{marker}` | floats after ASCII marker |
| `extract:slice:{start}:{len}` | substring |
| `extract:literal:{text}` | constant |

Points with the same resolved `req:` are **grouped** — one TCP/UDP exchange per poll.

Example (ASCII serial-over-TCP, no checksum): [examples/framed-serial-tcp](readme.md).

Response schema: `value`, `raw`, `bytesRead` (STRING/STRING/INTEGER).

### mbus (`ispf-driver-mbus`)

M-Bus meter read (jmbus 3.x). Config: TCP (`host`, `port`) or serial (`serialPort`).

Point mapping: `primary:secondary:register`.

### omron-fins (`ispf-driver-omron-fins`)

FINS/TCP. Config: `host`, `port` (9600), `destNode`, `srcNode`.

Point mapping: `DM:100:1` (area:address:count).

### asterisk (`ispf-driver-asterisk`)

Asterisk AMI over TCP: banner → `Action: Login` → action block → parsed response. Config: `host`, `port` (5038), `username`, `secret`.

Point mapping: verbatim AMI action block (for example `Action: Ping`). Variable: `value` (first `Ping:`/`Message:` header or whole response), `response` (raw), `success`.

Maturity: **production**. Loopback test: `AsteriskDeviceDriverTest` (in-test AMI server). Read-only. Limitations: socket timeouts hardcoded 5 s; one TCP connection per point poll; login response is not validated.

### sip (`ispf-driver-sip`)

SIP probe (JAIN-SIP factories, raw UDP exchange). Config: `host`, `port` (5060), `username`, `domain`, `timeoutMs`.

Point mapping: `options` (OPTIONS ping; reachable on 2xx/3xx) or `register` (REGISTER probe; 200 = `registered`, 401/407 = `challenge`). Variable: `reachable`, `statusCode`, `value`.

Maturity: **production**. Loopback test: `SipDeviceDriverTest` (in-test UDP SIP responder). Read-only. Note: requires the log4j-1.2 API at runtime — the module bundles `reload4j` (jain-sip-ri declares log4j as `provided`).

### radius (`ispf-driver-radius`)

RADIUS authentication check (TinyRadius, **PAP only**). Config: `host`, `port` (1812), `secret`, `username`, `password`, `timeoutMs`.

Point mapping: `auth`. Variable: `value` (`success`/`fail`), `success`, `responseCode` (RADIUS packet type; `-1` when no response — errors collapse to failure without exceptions).

Maturity: **production**. Loopback test: `RadiusDeviceDriverTest` (in-process TinyRadius `RadiusServer`: Access-Accept / Access-Reject / unreachable). Read-only. Limitations: no CHAP/MS-CHAP, no accounting.

### smpp (`ispf-driver-smpp`)

SMPP 3.x (jsmpp). Config: `host`, `port`, `systemId`, `password`.

Point mapping: `bind` (status) or `destination:message` (submit).

### smb (`ispf-driver-smb`)

SMB/CIFS (smbj). Config: `host`, `share`, `username`, `password`, `domain`.

Point mapping: file path in share → `exists`, `size`.

### ldap (`ispf-driver-ldap`)

LDAP directory checks (UnboundID SDK). Config: `host`, `port` (389), `bindDn`, `password`, `useSsl`.

Point mapping: an LDAP filter `(objectClass=person)` → entry count, or `filter:attribute` (split at the last `:`; filter auto-wrapped in parens) → attribute value, for example `cn=admin:mail`. Variable: `value`, `count`.

Maturity: **production**. Loopback test: `LdapDeviceDriverTest` (UnboundID `InMemoryDirectoryServer`). Read-only. Limitations: search base DN is hardcoded to the root DSE (`""`) — real directories may reject anonymous null-base searches; a fresh connection per poll.

### dhcp (`ispf-driver-dhcp`)

DHCP DISCOVER probe (minimal hand-built client). Config: `interfaceName`, `bindAddress`, `timeoutMs`, plus optional `serverPort` (67), `listenPort` (68), `broadcastAddress` (`255.255.255.255`) — the defaults require privileged ports and broadcast capability.

Point mapping: `serverIp` (option 54, falls back to yiaddr) or `lease` (`obtained`/`none`). Variable: `value`, `leased`, `leaseSeconds` (option 51).

Maturity: **production**. Loopback test: `DhcpDeviceDriverTest` (in-test UDP OFFER responder via the injectable ports). Read-only. Limitation: DISCOVER only — no REQUEST/renew, only options 54/51 parsed.

### ingress-syslog (`ispf-driver-ingress-syslog`)

UDP syslog **raw datagram capture** (default port 514; `bindAddress` honored). No RFC5424/3164 field extraction — message text is forwarded unparsed.

Each datagram → fixed variable `lastDatagram` (`message`, `sourceHost`, `sourcePort`, `bytes`) with `observedAt`. `readPoints` publishes stats (`messagesReceived`, `lastMessage`, `listening`) to every mapped point.

Maturity: **production**. Loopback test: `SyslogIngressDeviceDriverTest` (datagram → record + stats). Read-only.

### ingress-snmp-trap (`ispf-driver-ingress-snmp-trap`)

SNMP trap **raw capture** on UDP/162 (privileged on Linux; `bindAddress` honored). BER payload is stored as opaque bytes — OID/varbind decode is deliberately downstream (correlator/rules).

Each trap → fixed variable `lastTrap` (`payloadBase64`, `payloadHex` preview, `sourceHost`, `bytes`) with `observedAt`. `readPoints` → stats (`trapsReceived`, `listening`).

Maturity: **production**. Loopback test: `SnmpTrapIngressDeviceDriverTest`. Read-only.

### ingress-sflow (`ispf-driver-ingress-sflow`)

sFlow v5 **raw datagram capture** on UDP/6343 (`bindAddress` honored). Flow-record decode is downstream by design.

Each datagram → fixed variable `lastDatagram` (`payloadBase64`, `sourceHost`, `bytes`) with `observedAt`. `readPoints` → stats (`datagramsReceived`, `listening`).

Maturity: **production**. Loopback test: `SflowIngressDeviceDriverTest`. Read-only.

## Adding your own driver

1. Create module `packages/ispf-driver-xxx`, dependency on `ispf-driver-api`.
2. Implement `DeviceDriver`.
3. Register in `DriverCatalog` (`ispf-server`).
4. Add `implementation(project(...))` in `ispf-server/build.gradle.kts`.
5. Define a model with driver variables or apply a `MIXIN` model to `DEVICE`.

## Diagnostics

- Logs: `com.ispf.server.driver` (DEBUG level in `local`/`dev`)
- `driverStatus` on the device object
- WARN in log on poll error (SNMP timeout); optional OID — once DEBUG, poll continues
