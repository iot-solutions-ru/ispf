# OT Trust — BL-141 fixture depth (EtherNet/IP)

Date: 2026-09-06  
Branch tip: `cursor/ot-trust-bl141-ethernet-ip`

## Goal

Continue BL-141 depth after IEC 104 (#150): add a stdlib **EtherNet/IP** CIP UCMM TCP peer for TOP-20 `ethernet-ip`. Wire subset matches the Java loopback emulator (`RegisterSession` / `SendRRData` / Read Tag `0x4C` / Write Tag `0x4D`), but **persists writes** so smoke can read back.

## Added fixture

| Service | Port | Smoke | Notes |
|---------|------|-------|-------|
| `ethernet-ip` | TCP `127.0.0.1:44818` | CIP Write Tag + Read Tag on `Program:MainProgram.Counter` (DINT) | Session handle **42**; seed **12345678**; stdlib only |

## Self-tests (no docker)

```bash
deploy/tools/driver-interop-smoke.sh --self-test-ethernet-ip
```

## TOP-20 compose coverage (after this change)

| Layer | Covered |
|-------|---------|
| Docker compose fixtures | **8 / 20** (`mqtt`, `modbus-tcp`, `opcua`, `snmp`, `http`, `bacnet`, `iec104`, `ethernet-ip`) |
| Fixture smoke | **8 / 20** |
| Gradle interop modules in CI | **20 / 20** (unchanged) |

## Deferred / next

- **DLMS** — WRAPPER subset peer when ready
- **S7** — needs external soft PLC (not stdlib)
- **DNP3** — poll-only (ADR-0057); lower write-smoke value
- Plant soaks for BL-140 field Done

## Honesty

Lab docker fixtures ≠ field certification / OT scorecard 10/10 / BL-140 plant Done.
