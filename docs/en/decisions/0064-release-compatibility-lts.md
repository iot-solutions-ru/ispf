# ADR-0064: Release, compatibility and LTS policy

## Status

**Accepted** (2026-10-12) — Wave 1 lands `formatVersion` on application bundles; platform LTS and public API freeze remain pre-1.0 policy until the first LTS tag.

## Context

ISPF is still on `0.9.x`. Operators and solution authors need a written contract for:

1. What breaks across platform upgrades (API, driver SPI, bundle manifest shape, Flyway).
2. How long a release line is supported.
3. How to evolve the bundle JSON without silent corruption (`additionalProperties: true` today).

Related gaps: [0037](0037-relational-core-portability.md) described a “baseline-only forever” story while PostgreSQL already ships incremental `V71+`; [0007](0007-bundle-tree-packaging.md) versions the **product** with semver `version` but not the **wire format**.

## Decision

### 1. Three version axes

| Axis | Field / artifact | Meaning |
|------|------------------|---------|
| Platform release | `gradle.properties` / image tag | Product build (`0.9.x` until 1.0) |
| Bundle product version | manifest `version` (semver) | Solution revision; deploy history per `appId` ([0007](0007-bundle-tree-packaging.md)) |
| Bundle **format** | manifest `formatVersion` (integer) | Wire schema of the JSON; independent of solution semver |

### 2. `formatVersion` (Wave 1 — landed)

1. Optional integer on the root of `bundle.json`. **Omit ⇒ `1`**.
2. Current value: **`1`**. Supported range on this build: **`1…1`** (`BundleFormatVersions`).
3. Validate/deploy **rejects** unsupported format versions with a clear error.
4. Pull-from-tree **writes** `formatVersion: 1`.
5. JSON Schema documents `formatVersion` with `const: 1`. Tightening `additionalProperties` waits for a future format bump (do not break existing packs in wave 1).
6. Bumping to `2` requires: schema change, dual-read or migrate path, and raising `MAX_SUPPORTED` only after readers exist.

### 3. Platform compatibility (pre-1.0 and toward 1.0)

Until the first **1.0.0** tag:

- Semver for the platform follows Keep a Changelog; **breaking** changes are allowed on `0.9.x` but must be called out in `CHANGELOG.md`.
- Public HTTP `/api/v1/**` and driver SPI avoid silent semantic changes; prefer additive fields.
- After **1.0.0**: minor/patch must remain backward compatible for documented surfaces; major may break with a migration guide.

**LTS (after 1.0):** an LTS line supports **N** and **N−1** platform majors for security fixes for at least **12 months** from the next major GA, unless a named customer contract says otherwise. Revisit when the first LTS customer asks for N−2.

### 4. Flyway expand/contract (amends 0037)

PostgreSQL metadata DB:

1. `V1__baseline.sql` = squash of legacy V1–V70.
2. **`V71+` incremental scripts** are the normal upgrade path on the same engine.
3. Prefer **expand/contract**: additive DDL first; remove columns/tables only after a release that no longer reads them.
4. Engine change (PG → MSSQL, …) remains **greenfield + bundle import**, not live migration.
5. Non-PG packs (`mssql` / `mysql` / `oracle`) stay **not started** until a certified baseline exists.

### 5. Driver SPI and ui-pack

- Driver packs declare compatibility against a platform major (existing pack metadata); a platform minor must not require pack rebuilds unless the changelog says otherwise.
- Hosted ui-packs ([0054](0054-hosted-ui-packs.md)) follow the same `/api/v1` compatibility rules; trust isolation is out of this ADR.

## Consequences

- Fitness: `BundleFormatVersionTest`, existing `BundleSemverContractTest`, schema validation via `bundle.schema.json`.
- Docs: [applications](../applications.md), [0007](0007-bundle-tree-packaging.md), [0037](0037-relational-core-portability.md).
- CI rolling-upgrade proof (cluster N → N+1) remains a follow-up gate, not wave 1.

### Risks

- Authors may confuse `version` and `formatVersion` — mitigated by docs and validator messages.
- Leaving `additionalProperties: true` on format 1 still allows typos at the root; format 2 should tighten.

## Related

- [0007-bundle-tree-packaging](0007-bundle-tree-packaging.md)
- [0037-relational-core-portability](0037-relational-core-portability.md)
- [0054-hosted-ui-packs](0054-hosted-ui-packs.md)
- [0059-toolchain-version-policy](0059-toolchain-version-policy.md)
