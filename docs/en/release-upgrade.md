> **Language:** Canonical English. Russian edition: [ru/release-upgrade.md](../ru/release-upgrade.md).

# Release / bundle upgrade compatibility

> **Status:** Lab gate (ADR-0064 Wave 2). Hub: [doc-status.md](doc-status.md).

What ISPF proves today about upgrading solutions and platforms — and what it does **not**.

Policy source: [0064-release-compatibility-lts](decisions/0064-release-compatibility-lts.md). Bundle wire field: `manifest.formatVersion` (omit ⇒ `1`). Semver product field: `manifest.version` — see [applications](applications.md) § Semver and format contract.

## Proven in CI / unit tests (lab)

| Gate | Proof | Honesty |
|------|-------|---------|
| Accept current format | `BundleFormatVersionTest` — omit or `formatVersion: 1` validates | Lab only |
| Reject unsupported format | Same test — `formatVersion: 99` → ERROR | Lab only |
| Catalog pilot validates + redeploys | `BundleUpgradeCompatGateTest` — `examples/marketplace-catalog/marketplace-demo` validates at format `1`, local marketplace install succeeds twice (redeploy same `appId`) | Lab H2 / MockMvc — **not** a production rolling upgrade |
| Semver major bump warn | Existing validate path on major `version` bumps | Docs in applications.md |

Evidence note: [release-compat/2026-10-12-bundle-upgrade-lab-gate.md](../evidence/release-compat/2026-10-12-bundle-upgrade-lab-gate.md).

## Not proven yet (explicit residual)

| Gap | Why |
|-----|-----|
| **Cluster image N → N+1** rolling upgrade | Gate defined in [0070-cluster-rolling-upgrade](decisions/0070-cluster-rolling-upgrade.md); dual-image CI soak still open |
| Live customer plant upgrade journals | Field evidence, not CI |
| Bundle `formatVersion: 2` dual-read | No format bump yet; tighten `additionalProperties` only with a bump |

Do **not** claim scorecard Deploy/scale 10/10 or “zero-downtime upgrade” from this lab gate.

## Operator checklist (lab / demostand)

1. Confirm platform build documents supported `formatVersion` range (`BundleFormatVersions.MIN_SUPPORTED`…`MAX_SUPPORTED`).
2. Validate bundle (`ispf pack` / `POST …/bundle/validate`) before deploy.
3. Redeploy same `appId` to exercise reconcile (safe for marketplace-demo).
4. For major **product** semver bumps, read validate warnings and migration notes.

## Related

- [0064-release-compatibility-lts](decisions/0064-release-compatibility-lts.md)
- [0070-cluster-rolling-upgrade](decisions/0070-cluster-rolling-upgrade.md)
- [0069-cluster-consistency](decisions/0069-cluster-consistency.md)
- [applications](applications.md) — deploy / format contract
- [deployment](deployment.md) — install / Helm
- [marketplace](marketplace.md) — catalog pilots
