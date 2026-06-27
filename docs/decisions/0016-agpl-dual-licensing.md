# ADR-0016: AGPL platform + driver packs + dual licensing

Статус: **Accepted**  
Дата: 2026-06-27

## Контекст

ISPF переходит с Apache 2.0 core на **AGPL-3.0** с **dual licensing** (community + commercial EULA).
Все device drivers выносятся из `ispf-server.jar` в **driver packs** (1 driver = 1 pack = 1 license).

## Решение

1. **Platform** (`ispf-server`, `web-console`, monorepo core) — **GNU AGPL v3**; commercial exemption через `platform-license.json`.
2. **Drivers** — только через `${ISPF_DRIVER_PACKS_DIR}` / `ispf.driver.packs-dir`; Gradle task `syncAllDriverPacks`.
3. **`driver-pack.json`** — поля `packId`, `driverId`, `licenseType`, `drivers[]`, optional RSA `license`.
4. **Application bundles** — отдельный продукт со своей EULA; RSA deploy (ADR-0003) без изменений.
5. **License boundaries:**
   - Platform **source** modifications + network use → AGPL (or commercial EULA).
   - Declarative **bundle** JSON → не platform source; лицензируется отдельно.
   - Driver pack → `licenseType` per pack + third-party notices.

## Последствия

- Breaking: пустой `packs-dir` → нет драйверов в runtime.
- `DriverFactory` / `DriverCatalog` — только loaded packs.
- `GET /api/v1/platform/license` — tier/status.
- Docs: [LICENSE.md](../LICENSE.md), [LICENSED_DRIVER_PACKS.md](../LICENSED_DRIVER_PACKS.md).

## Связанные материалы

- Supersedes marketing «Apache-only core» in [0003](0003-commercial-bundle-licensing.md) scope (bundle licensing unchanged).
- [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)
