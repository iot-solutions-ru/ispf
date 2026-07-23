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
| `dnp3` | PRODUCTION (poll only) | **BETA** (BL-191) | Class 0/1/2/3 poll; **write not implemented** |
| `ethernet-ip` | PRODUCTION | **BETA** (BL-191) | CIP session registration + tag path placeholder |
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
