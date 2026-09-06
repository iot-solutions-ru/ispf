# OT Trust — BL-141 fixture depth (SNMP + HTTP)

Date: 2026-09-06  
Branch tip: `cursor/ot-trust-bl141-fixtures`

## Goal

After Waves 1–11 catalog close (#144) and post-merge honesty (#147), deepen **BL-141 docker fixtures** for TOP-20 — do **not** invent more stub promotions.

## Added fixtures

| Service | Port | Smoke | Notes |
|---------|------|-------|-------|
| `snmp` | UDP `127.0.0.1:161` | GET/SET `1.3.6.1.4.1.99999.1.1.0` | Stdlib SNMPv2c lab agent; community `public` (read+write in lab) |
| `http` | TCP `127.0.0.1:8089` | PUT/GET `/points/gauge` | Stdlib JSON gauge; also `/health` |

Existing fixtures unchanged: mosquitto `1883`, modbus-tcp `502`, opcua `4840`.

## Self-tests (no docker)

```bash
deploy/tools/driver-interop-smoke.sh --self-test-modbus
deploy/tools/driver-interop-smoke.sh --self-test-snmp
deploy/tools/driver-interop-smoke.sh --self-test-http
```

## TOP-20 compose coverage (after this change)

| Layer | Covered |
|-------|---------|
| Docker compose fixtures | **5 / 20** (`mqtt`, `modbus-tcp`, `opcua`, `snmp`, `http`) |
| Fixture smoke | **5 / 20** (same + optional opcua write) |
| Gradle interop modules in CI | **20 / 20** (unchanged) |

Still no compose peers for: `virtual`/`flexible` (in-process), `modbus-rtu`/`modbus-udp`, `opcua-server`, `bacnet`, `s7`, `iec104`/`iec104-server`, `dnp3`, `dlms`, `ethernet-ip`, `opc-da`/`opc-bridge`, `gps-tracker`.

## Honesty

Lab docker fixtures ≠ field certification / OT scorecard 10/10 / BL-140 plant Done.  
`opc-da` / `opc-bridge` remain `SHELL_BETA`.
