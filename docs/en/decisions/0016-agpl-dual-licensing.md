# ADR-0016: AGPL platform + driver packs + dual licensing

## Status

Accepted (2026-06-27)

## Context

ISPF is moving from an Apache 2.0 core to **AGPL-3.0** with **dual licensing** (community + commercial EULA).
All device drivers are extracted from `ispf-server.jar` into **driver packs** (1 driver = 1 pack = 1 license).

## Decision

1. **Platform** (`ispf-server`, `web-console`, monorepo core) — **GNU AGPL v3**; commercial exemption via `platform-license.json`.
2. **Drivers** — only through `${ISPF_DRIVER_PACKS_DIR}` / `ispf.driver.packs-dir`; Gradle task `syncAllDriverPacks`.
3. **`driver-pack.json`** — fields `packId`, `driverId`, `licenseType`, `drivers[]`, optional RSA `license`.
4. **Application bundles** — separate product with its own EULA; RSA deploy (ADR-0003) unchanged.
5. **License boundaries:**
   - Platform **source** modifications + network use → AGPL (or commercial EULA).
   - Declarative **bundle** JSON → not platform source; licensed separately.
   - Driver pack → `licenseType` per pack + third-party notices.

## Consequences

- Breaking: empty `packs-dir` → no drivers at runtime.
- `DriverFactory` / `DriverCatalog` — loaded packs only.
- `GET /api/v1/platform/license` — tier/status.
- Docs: [LICENSE](../LICENSE.md), [licensed-driver-packs](../licensed-driver-packs.md).

## Related

- Supersedes marketing «Apache-only core» in [0003-commercial-bundle-licensing](0003-commercial-bundle-licensing.md) scope (bundle licensing unchanged).
- [third-party-notices](../third-party-notices.md)
