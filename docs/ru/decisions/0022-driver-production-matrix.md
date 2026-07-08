> **Язык:** русская версия (вычитка). Канонический английский: [en/decisions/0022-driver-production-matrix.md](../../en/decisions/0022-driver-production-matrix.md).

# ADR-0022: Driver production matrix

**Статус:** Принято  
**Дата:** 30 июня 2026 г.  
**Контекст:** BL-78 (REQ-EX Wave J). `DriverMaturityRegistry` держал ad-hoc labels; promotion в PRODUCTION не имел objective criteria и CI enforcement.

## Решение

1. **Single matrix** — `DriverProductionMatrix` (server) — source of truth для `DriverMaturity` и declared **capabilities** per driver id.
2. **Maturity levels**
   - **PRODUCTION** — loopback или integration test в CI; documented в `drivers.md`; write path где applicable; poll path stable.
   - **BETA** — implemented read/write path или connectivity check; test coverage partial или hardware-dependent.
   - **STUB** — placeholder driver pack (не в matrix; defaults to BETA в catalog).
3. **Capabilities** (declared, не все implemented yet): `POLL`, `SUBSCRIBE`, `WRITE`, `DISCOVERY`, `QUALITY`, `OBSERVED_AT`.
4. **CI gate** — `DriverProductionMatrixTest` fails, если любая PRODUCTION row не имеет resolvable `loopbackTestClass`.
5. **Dogfooding** — new driver promotion следует [0002](0002-dogfooding-gate.md); matrix row добавляется только после acceptance criteria.

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

`jdbc` / `kafka` ingress driver'ы **out of scope** для driver-pack matrix; telemetry ingest — Wave O (ADR-0024 planned).

## Последствия

- `DriverMaturityRegistry` delegates to matrix.
- BL-79…85 extend capabilities (`OBSERVED_AT`, `QUALITY`, CI matrix workflow).
- Operator UI продолжает использовать `DriverMetadata.maturity()` из catalog merge.

## Связанные материалы

- [drivers.md](../drivers.md)
- [roadmap.md § BL-78](../roadmap.md#часть-e--полный-реестр-bl-01139)
- [0020-time-and-timezones.md](0020-time-and-timezones.md) — `observedAt`
