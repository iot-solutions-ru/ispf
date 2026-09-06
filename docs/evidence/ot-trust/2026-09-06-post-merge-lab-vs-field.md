# OT Trust post-merge — lab catalog vs field certification

Date: 2026-09-06  
Tip: `main` after [#144](https://github.com/iot-solutions-ru/ispf/pull/144) (`b3b06182`)

## What closed

| Gate | Status |
|------|--------|
| Protocol stub catalog (`protocol-stub-ids.json`) | **0** stubs |
| Matrix / audit catalog | **162 / 162** |
| Audit FAIL / WARN | **0 / 0** |
| Codec promotion waves | Waves **1–11** evidence under this folder |

Honesty line (unchanged): **Lab/matrix readiness only — not field certification for 162 drivers.**

Do **not** claim competitive-scorecard OT **10/10**, BL-140 field Done, or vendor-stack certification from this merge.

## Remaining honesty residuals

| driverId | maturity | readiness | Note |
|----------|----------|-----------|------|
| `opc-da` | BETA | `SHELL_BETA` | Top-20 shell — not PRODUCTION |
| `opc-bridge` | BETA | `SHELL_BETA` | Top-20 shell — not PRODUCTION |
| `corba` | BETA | `PARTIAL` | Incomplete; outside top-20 |

Many Wave 6–11 packs are **TCP/UDP gateway / protocol-subset labs** (clean-room JDK). They satisfy matrix loopback + pack contracts; they are **not** certified field stacks (no PROFINET RT/IRT, EtherCAT hard RT, full IEC 61850 SCL, vendor SDKs, etc.).

## Top-20 industrial — interop / smoke coverage

Source of ids: `DriverProductionMatrix.TOP_20_INDUSTRIAL` / audit `summary.top20`.

Legend:

- **Gradle interop in CI** — pack listed in driver-interop workflow module matrix (`interop_in_workflow`)
- **Docker compose fixture** — service in `deploy/driver-interop/docker-compose.yml`
- **Fixture smoke** — exercised by `deploy/tools/driver-interop-smoke.sh`

| driverId | maturity | readiness | WRITE | Gradle interop in CI | Docker compose fixture | Fixture smoke |
|----------|----------|-----------|-------|----------------------|------------------------|---------------|
| `virtual` | PRODUCTION | `READY_LAB` | n | ✓ | — | — (in-process only) |
| `mqtt` | PRODUCTION | `READY_LAB` | Y | ✓ | ✓ mosquitto | ✓ pub/sub |
| `modbus-tcp` | PRODUCTION | `READY_LAB` | Y | ✓ | ✓ writable lab | ✓ FC6/FC16 + FC3 |
| `modbus-rtu` | PRODUCTION | `READY_LAB` | Y | ✓ | — | — |
| `modbus-udp` | PRODUCTION | `READY_LAB` | Y | ✓ | — | — |
| `opcua` | PRODUCTION | `READY_LAB` | Y | ✓ | ✓ opc-plc | ✓ TCP + optional write |
| `opcua-server` | PRODUCTION | `READY_LAB` | Y | ✓ | — | — |
| `snmp` | PRODUCTION | `READY_LAB` | Y | ✓ | ✓ stdlib agent | ✓ GET/SET |
| `bacnet` | PRODUCTION | `READY_LAB` | Y | ✓ | ✓ stdlib agent | ✓ Read/WriteProperty |
| `s7` | PRODUCTION | `READY_LAB` | Y | ✓ | — | — |
| `http` | PRODUCTION | `READY_LAB` | Y | ✓ | ✓ JSON gauge | ✓ PUT/GET |
| `flexible` | PRODUCTION | `READY_LAB` | n | ✓ | — | — (in-process only) |
| `iec104` | PRODUCTION | `READY_LAB` | Y | ✓ | ✓ stdlib outstation | ✓ C_SE_NC/C_RD |
| `iec104-server` | PRODUCTION | `READY_LAB` | Y | ✓ | — | — |
| `dnp3` | PRODUCTION | `READY_LAB` | n | ✓ | — | — (poll-only, ADR-0057) |
| `dlms` | PRODUCTION | `READY_LAB` | Y | ✓ | — | — |
| `ethernet-ip` | PRODUCTION | `READY_LAB` | Y | ✓ | — | — |
| `opc-da` | BETA | `SHELL_BETA` | n | ✓ | — | — |
| `opc-bridge` | BETA | `SHELL_BETA` | n | ✓ | — | — |
| `gps-tracker` | PRODUCTION | `READY_LAB` | n | ✓ | — | — |

### Coverage summary (TOP-20)

| Layer | Covered | Gap |
|-------|---------|-----|
| Gradle / loopback interop modules in CI | **20 / 20** | — |
| Docker compose fixtures | **7 / 20** (`mqtt`, `modbus-tcp`, `opcua`, `snmp`, `http`, `bacnet`, `iec104`) | 13 without compose peer |
| Fixture smoke script | **7 / 20** | same gap |

`virtual` / `flexible` are intentionally in-process (no external peer required).

## Next depth (not catalog width)

Preferred order after catalog close — deepen BL-141 fixtures, do **not** invent more stub promotions:

1. ~~**SNMP**~~ — done (stdlib agent + GET/SET smoke) — see [2026-09-06-bl141-snmp-http-fixtures.md](2026-09-06-bl141-snmp-http-fixtures.md)
2. ~~**HTTP**~~ — done (writable JSON gauge + PUT/GET smoke)
3. ~~**BACnet**~~ — done — see [2026-09-06-bl141-bacnet-fixture.md](2026-09-06-bl141-bacnet-fixture.md); ~~**IEC 104**~~ done — see [2026-09-06-bl141-iec104-fixture.md](2026-09-06-bl141-iec104-fixture.md); next **S7** (external soft PLC) / **DNP3** / **DLMS** / **EtherNet/IP**
4. Plant pilots + soak journals ([pilot-soak-journal.template.md](pilot-soak-journal.template.md)) for BL-140 field Done  

Until (4), P-OT stays **In progress** for *field* trust — lab catalog width is closed.
