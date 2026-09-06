# OT Trust — BL-141 fixture depth (Modbus RTU)

Date: 2026-09-06  
Branch tip: `cursor/ot-trust-bl141-modbus-rtu`

## Goal

Continue BL-141 depth after S7 SoftPlc (#155): add a **Modbus RTU** peer for TOP-20 `modbus-rtu`. Framing is **RTU ADU** (unit + PDU + CRC-16) on a TCP stream — the common lab “serial server” bridge. Same holding map / FC3·FC6·FC16 as the TCP/UDP fixtures. Lab ≠ field Done.

## Added fixture

| Service | Port | Smoke | Notes |
|---------|------|-------|-------|
| `modbus-rtu` | TCP `127.0.0.1:5020` | FC6 + FC16 write → FC3 read-back | RTU ADU over TCP (not RS-485 / PTY); seed holdings `[0x1111,0x2222,0x3333]`; unit **1** |

## Self-tests (no docker)

```bash
deploy/tools/driver-interop-smoke.sh --self-test-modbus-rtu
```

## Honesty / scope

- This peer proves **RTU CRC framing + FC6/FC16/FC3** on a TCP bridge.
- It is **not** an RS-485 PHY, Linux PTY pair, or j2mod `ModbusSerialMaster` serial path.
- Java **j2mod** serial matrix / loopback remains the real serial codec proof (`serialPort`, 9600 8N1 defaults).
- Lab ≠ plant certification / OT scorecard 10/10 / BL-140 field Done.

## TOP-20 compose coverage (after this change)

| Layer | Covered |
|-------|---------|
| Docker compose fixtures | **13 / 20** (`mqtt`, `modbus-tcp`, `modbus-udp`, `modbus-rtu`, `opcua`, `snmp`, `http`, `bacnet`, `iec104`, `ethernet-ip`, `dlms`, `dnp3`, `s7`) |
| Fixture smoke | **13 / 20** |
| Gradle interop modules in CI | **20 / 20** (unchanged) |

## Deferred / next

- **gps-tracker** — driver is the TCP listener (awkward peer shape)
- Optional later: socat PTY pair bind-mounted for j2mod serial smoke
- Plant soaks for BL-140 field Done
