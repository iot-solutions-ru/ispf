# OT Trust — BL-141 fixture depth (BACnet/IP)

Date: 2026-09-06  
Branch tip: `cursor/ot-trust-bl141-bacnet`

## Goal

Continue BL-141 depth after SNMP/HTTP (#148): add a stdlib **BACnet/IP** UDP lab peer for TOP-20 `bacnet`. Prefer clean-room Python over soft-PLC images (S7 deferred).

## Added fixture

| Service | Port | Smoke | Notes |
|---------|------|-------|-------|
| `bacnet` | UDP `127.0.0.1:47808` | ReadProperty + WriteProperty on `analog-value:1` `present-value` (REAL) | Device instance **1001**; mirrors `BacnetLoopbackServer` wire subset |

Existing fixtures unchanged: mosquitto, modbus-tcp, opcua, snmp, http.

## Self-tests (no docker)

```bash
deploy/tools/driver-interop-smoke.sh --self-test-bacnet
```

## TOP-20 compose coverage (after this change)

| Layer | Covered |
|-------|---------|
| Docker compose fixtures | **6 / 20** (`mqtt`, `modbus-tcp`, `opcua`, `snmp`, `http`, `bacnet`) |
| Fixture smoke | **6 / 20** |
| Gradle interop modules in CI | **20 / 20** (unchanged) |

## Deferred / next

- **S7** — needs external soft PLC (no owned ISO-on-TCP loopback); not stdlib
- **IEC 104** — feasible stdlib TCP next (STARTDT + ASDU)
- **DNP3** — poll-only (ADR-0057); lower write-smoke value
- Plant soaks for BL-140 field Done

## Honesty

Lab docker fixtures ≠ field certification / OT scorecard 10/10 / BL-140 plant Done.
