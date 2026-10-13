# ADR-0016: AGPL platform + driver packs + dual licensing

> **Number alias:** **0016b** (AGPL + dual licensing). The same numeric id also labels [0016-clickhouse-event-journal](0016-clickhouse-event-journal.md) (**0016a**). Prefer the **filename or alias** in new links; do not renumber files.

## Status

**Accepted** (2026-06-27) — platform license boundary and driver-pack delivery model.

## Context

ISPF moved from an Apache 2.0 core to **AGPL-3.0** with **dual licensing** (community AGPL + commercial EULA). Device drivers ship as **driver packs** outside `ispf-server.jar` so OT connectivity can be licensed and audited separately from the platform core.

**Industry bar:** open-core / dual-license platforms (and OT vendors with protocol packs) keep engine source obligations distinct from protocol IP. ISPF makes that boundary enforceable at load time (`packs-dir`), not only in legal text.

## Decision

1. **Platform** (`ispf-server`, `web-console`, monorepo core) — **GNU AGPL v3**; commercial exemption via `platform-license.json`.
2. **Drivers** — only through `${ISPF_DRIVER_PACKS_DIR}` / `ispf.driver.packs-dir`; Gradle task `syncAllDriverPacks`.
3. **`driver-pack.json`** — fields `packId`, `driverId`, `licenseType`, `drivers[]`, optional RSA `license` ([0003](0003-commercial-bundle-licensing.md)).
4. **Application bundles** — separate product with its own EULA; RSA deploy ([0003](0003-commercial-bundle-licensing.md)) unchanged.
5. **License boundaries:**
   - Platform **source** modifications + network use → AGPL (or commercial EULA).
   - Declarative **bundle** JSON → not platform source; licensed separately.
   - Driver pack → `licenseType` per pack + third-party notices.

### Commercial packaging honesty (sales)

| Model | Status | Notes |
|-------|--------|-------|
| Empty `packs-dir` → no drivers at runtime | **Done** (by design) | Forces explicit pack install |
| One Gradle/module driver → one pack artifact | **Done** (engineering default) | `1 driver ≈ 1 pack` in repo layout |
| One commercial SKU = one pack | **Default sales story** | Matches engineering layout |
| Site / Enterprise SKU covering **many** packs | **Allowed commercially** | Contract/EULA may bundle packs; runtime still loads discrete pack files — do not invent a single mega-JAR |
| Online license server / floating seats | **Gap** | Not claimed ([0003](0003-commercial-bundle-licensing.md)) |

Do **not** tell buyers that “one driver = one invoice line” is a hard runtime law — it is the **default pack shape**. Enterprise bundling is a commercial overlay on the same pack files.

## Consequences

- Breaking: empty `packs-dir` → no drivers at runtime.
- `DriverFactory` / `DriverCatalog` — loaded packs only.
- `GET /api/v1/platform/license` — tier/status.
- Docs: [license](../license.md), [licensed-driver-packs](../licensed-driver-packs.md).
- Cite as **0016b** in new docs.

## Related

- [0003-commercial-bundle-licensing](0003-commercial-bundle-licensing.md) — bundle RSA (not core AGPL)
- [0016-clickhouse-event-journal](0016-clickhouse-event-journal.md) — **0016a**
- [third-party-notices](../third-party-notices.md)
