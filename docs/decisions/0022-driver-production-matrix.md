# ADR-0022: Driver production matrix

**Status:** Accepted  
**Date:** 2026-06-30  
**Context:** BL-78 (REQ-EX Wave J). `DriverMaturityRegistry` held ad-hoc labels; promotion to PRODUCTION lacked objective criteria and CI enforcement.

## Decision

1. **Single matrix** — `DriverProductionMatrix` (server) is the source of truth for `DriverMaturity` and declared **capabilities** per driver id.
2. **Maturity levels**
   - **PRODUCTION** — loopback or integration test in CI; documented in `DRIVERS.md`; write path where applicable; poll path stable.
   - **BETA** — implemented read/write path or connectivity check; test coverage partial or hardware-dependent.
   - **STUB** — placeholder driver pack (not in matrix; defaults to BETA in catalog).
3. **Capabilities** (declared, not all implemented yet): `POLL`, `SUBSCRIBE`, `WRITE`, `DISCOVERY`, `QUALITY`, `OBSERVED_AT`.
4. **CI gate** — `DriverProductionMatrixTest` fails if any PRODUCTION row lacks a resolvable `loopbackTestClass`.
5. **Dogfooding** — new driver promotion follows [0002](0002-dogfooding-gate.md); matrix row added only after acceptance criteria.

## Top-10 industrial drivers (target PRODUCTION)

| Driver id | Maturity (2026-06) | Owner track | Notes |
| --------- | ------------------ | ----------- | ----- |
| virtual | PRODUCTION | platform | Lab + unified poll |
| mqtt | PRODUCTION | connectivity | Ingress + observedAt JSON |
| modbus-tcp | PRODUCTION | connectivity | Write FC5/FC6 |
| modbus-rtu | PRODUCTION | connectivity | Serial path |
| opcua | PRODUCTION | connectivity | Milo client; BL-80 subscribe |
| snmp | PRODUCTION | connectivity | v1/v2c/v3 |
| s7 | PRODUCTION | connectivity | ISO-on-TCP |
| http | PRODUCTION | connectivity | REST poll |
| bacnet | PRODUCTION | connectivity | network test + observedAt |
| flexible | PRODUCTION | connectivity | Template driver |

`jdbc` / `kafka` ingress drivers are **out of scope** for driver-pack matrix; telemetry ingress is Wave O (ADR-0024 planned).

## Consequences

- `DriverMaturityRegistry` delegates to matrix.
- BL-79…85 extend capabilities (`OBSERVED_AT`, `QUALITY`, CI matrix workflow).
- Operator UI continues to use `DriverMetadata.maturity()` from catalog merge.

## References

- [DRIVERS.md](../DRIVERS.md)
- [ROADMAP.md § BL-78](../ROADMAP.md#часть-e--полный-реестр-bl-01139)
- [0020-time-and-timezones.md](0020-time-and-timezones.md) — `observedAt`
