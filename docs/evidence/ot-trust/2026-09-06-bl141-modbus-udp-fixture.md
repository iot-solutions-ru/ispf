# OT Trust — BL-141 fixture depth (Modbus UDP)

Date: 2026-09-06  
Branch tip: `cursor/ot-trust-bl141-modbus-udp`

## Goal

Continue BL-141 depth after DLMS (#152): add a **Modbus UDP** peer for TOP-20 `modbus-udp`. Reuses the same stdlib MBAP+PDU fixture as TCP (`deploy/driver-interop/modbus/server.py --udp`), matching j2mod `ModbusUDPMaster` framing. Writes persist for FC6/FC16 → FC3 read-back.

## Added fixture

| Service | Port | Smoke | Notes |
|---------|------|-------|-------|
| `modbus-udp` | UDP `127.0.0.1:502` | FC6 + FC16 write → FC3 read-back | Same seed holdings `[0x1111,0x2222,0x3333]` as TCP; host `502/udp` alongside TCP `502` |

## Self-tests (no docker)

```bash
deploy/tools/driver-interop-smoke.sh --self-test-modbus
deploy/tools/driver-interop-smoke.sh --self-test-modbus-udp
```

## TOP-20 compose coverage (after this change)

| Layer | Covered |
|-------|---------|
| Docker compose fixtures | **10 / 20** (`mqtt`, `modbus-tcp`, `modbus-udp`, `opcua`, `snmp`, `http`, `bacnet`, `iec104`, `ethernet-ip`, `dlms`) |
| Fixture smoke | **10 / 20** |
| Gradle interop modules in CI | **20 / 20** (unchanged) |

## Deferred / next

- **DNP3** — poll-only (ADR-0057); integrity-poll smoke only
- **S7** — needs external soft PLC (not stdlib)
- **modbus-rtu** — needs PTY/serial pair
- Plant soaks for BL-140 field Done

## Honesty

Lab docker fixtures ≠ field certification / OT scorecard 10/10 / BL-140 plant Done.
