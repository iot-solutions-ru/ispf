# OT Trust — BL-141 fixture depth (GPS tracker)

Date: 2026-09-06  
Branch tip: `cursor/ot-trust-bl141-gps-tracker`

## Goal

Continue BL-141 depth after Modbus RTU (#156): add a lab peer for TOP-20 `gps-tracker`.
The Java driver is a **TCP listener** (devices connect in). Compose runs a stdlib
**listener stand-in** with the same line-oriented NMEA accept path plus HTTP `/last`
for smoke observability. Lab ≠ field Done.

## Added fixture

| Service | Port | Smoke | Notes |
|---------|------|-------|-------|
| `gps-tracker` | TCP `127.0.0.1:5005` (NMEA) + HTTP `127.0.0.1:5006` | Send SEED GGA → `/last` | Lab stand-in for driver listen/read-line; not a GNSS modem |

## Self-tests (no docker)

```bash
deploy/tools/driver-interop-smoke.sh --self-test-gps-tracker
```

## Honesty / scope

- Fixture mirrors **accept TCP + last non-blank line** — the gps-tracker driver contract.
- Java **GpsTrackerDeviceDriver** loopback tests remain the accept-loop proof.
- Smoke acts as the **device** (client feeding NMEA); compose is the lab listener.
- Lab ≠ field tracker / plant certification / OT 10/10 / BL-140 Done.

## TOP-20 compose coverage (after this change)

| Layer | Covered |
|-------|---------|
| Docker compose fixtures | **14 / 20** (`mqtt`, `modbus-tcp`, `modbus-udp`, `modbus-rtu`, `opcua`, `snmp`, `http`, `bacnet`, `iec104`, `ethernet-ip`, `dlms`, `dnp3`, `s7`, `gps-tracker`) |
| Fixture smoke | **14 / 20** |
| Gradle interop modules in CI | **20 / 20** (unchanged) |

## Deferred / next

- `virtual` / `flexible` — in-process only (no external peer)
- `opcua-server` / `iec104-server` — driver *is* the server (no separate peer)
- `opc-da` / `opc-bridge` — remain `SHELL_BETA`
- Optional later: outbound device-emulator feeder via `host.docker.internal`
- Plant soaks for BL-140 field Done
