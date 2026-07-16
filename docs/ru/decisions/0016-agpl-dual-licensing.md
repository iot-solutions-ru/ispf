> **Язык:** русская версия (вычитка). Канонический английский: [en/decisions/0016-agpl-dual-licensing.md](../../en/decisions/0016-agpl-dual-licensing.md).

# ADR-0016: AGPL platform + driver packs + dual licensing

Статус: **Принято**  
Дата: 2026-06-27

## Контекст

ISPF перешёл с Apache 2.0 core на **AGPL-3.0** с **dual licensing** (community + commercial EULA).
Все device driver'ы вынесены из `ispf-server.jar` в **driver packs** (1 driver = 1 pack = 1 license).

## Решение

1. **Platform** (`ispf-server`, `web-console`, monorepo core) — **GNU AGPL v3**; commercial exemption через `platform-license.json`.
2. **Drivers** — только через `${ISPF_DRIVER_PACKS_DIR}` / `ispf.driver.packs-dir`; Gradle task `syncAllDriverPacks`.
3. **`driver-pack.json`** — поля `packId`, `driverId`, `licenseType`, `drivers[]`, optional RSA `license`.
4. **Application bundles** — отдельный продукт со своей EULA; RSA deploy (ADR-0003) без изменений.
5. **License boundaries:**
   - Модификации **исходного кода** platform + network use → AGPL (или commercial EULA).
   - Декларативный **bundle** JSON → не platform source; лицензируется отдельно.
   - Driver pack → `licenseType` per pack + third-party notices.

## Последствия

- Breaking: пустой `packs-dir` → нет драйверов в runtime.
- `DriverFactory` / `DriverCatalog` — только loaded packs.
- `GET /api/v1/platform/license` — tier/status.
- Docs: [LICENSE](../LICENSE.md), [licensed-driver-packs](../licensed-driver-packs.md).

## Связанные материалы

- Supersedes marketing «Apache-only core» в scope [0003-commercial-bundle-licensing](0003-commercial-bundle-licensing.md) (bundle licensing без изменений).
- [third-party-notices](../third-party-notices.md)
