# OT Trust — BL-141 fixture depth (DLMS)

Date: 2026-09-06  
Branch tip: `cursor/ot-trust-bl141-dlms`

## Goal

Continue BL-141 depth after EtherNet/IP (#151): add a stdlib **DLMS** TCP WRAPPER peer for TOP-20 `dlms`. Wire subset matches the Java loopback (`DlmsTcpWrapperCodec` / `DlmsLoopbackServer`): Associate / GET / SET for Data+Register values. Writes **persist** so smoke can SET → GET read-back.

## Added fixture

| Service | Port | Smoke | Notes |
|---------|------|-------|-------|
| `dlms` | TCP `127.0.0.1:4059` | WRAPPER SET + GET on REGISTER `1.0.1.8.0.255` attr 2 | Client SAP **16**; seed **42.0**; also seeds DATA `0.0.42.0.0.255` = `ISPF-TEST` |

## Self-tests (no docker)

```bash
deploy/tools/driver-interop-smoke.sh --self-test-dlms
```

## TOP-20 compose coverage (after this change)

| Layer | Covered |
|-------|---------|
| Docker compose fixtures | **9 / 20** (`mqtt`, `modbus-tcp`, `opcua`, `snmp`, `http`, `bacnet`, `iec104`, `ethernet-ip`, `dlms`) |
| Fixture smoke | **9 / 20** |
| Gradle interop modules in CI | **20 / 20** (unchanged) |

## Deferred / next

- **S7** — needs external soft PLC (not stdlib)
- **DNP3** — poll-only (ADR-0057); lower write-smoke value
- Plant soaks for BL-140 field Done

## Honesty

Lab docker fixtures ≠ field certification / OT scorecard 10/10 / BL-140 plant Done. This peer speaks the ISPF compact WRAPPER dialect — not HDLC / full COSEM / Gurux field meters.
