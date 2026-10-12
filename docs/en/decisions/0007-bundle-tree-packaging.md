# ADR-0007: Bundle = tree packaging

## Status

Accepted (2026-06-21)

## Context

Solution developers deliver configuration through a single JSON manifest (`bundle.json`). We must record that a bundle is **packaging of the object tree and related artifacts**, not a separate runtime or platform fork.

## Decision

1. **Single manifest** — `POST /api/v1/applications/{appId}/deploy` accepts JSON with sections `objects`, `models`, `variables`, `functions`, `workflows`, `dashboards`, `events`, `alertRules`, `correlators`, `operatorUi`, `migrations`, optional `license`.
2. **Tree-first** — after deploy, nodes appear under `root.platform.*` (and app-specific paths); business logic is invoked through the object tree (`BFF invoke`, tree functions), not hardcoded routes in `main`.
3. **App schema separate** — application SQL migrations (`migrations[]`) apply to the app schema (`schemaName`, `tablePrefix`), not platform Flyway.
4. **Bundle product versioning** — `version` field (semver); deploy history stored per `appId`; dependencies via `requires[]` with `minVersion`.
5. **Bundle wire format** — optional integer `formatVersion` (omit ⇒ `1`); see [0064-release-compatibility-lts](0064-release-compatibility-lts.md). Distinct from product `version`.
6. **Commercial** — optional `license` section (see [0003-commercial-bundle-licensing](0003-commercial-bundle-licensing.md)); Apache reference bundles without `license`.

## Consequences

- [applications](../applications.md) and [solution-developer-public-api](../solution-developer-public-api.md) describe the stable manifest contract.
- AI artifact generation validates the manifest via `BundleManifestValidator` before deploy.
- Industry-specific Java in `ispf-server` remains forbidden ([0001-app-platform-boundary](0001-app-platform-boundary.md)).

## Related

- REQ-PF-01, Phase 5 tree-first — [roadmap](../roadmap.md)
- [0064-release-compatibility-lts](0064-release-compatibility-lts.md) — formatVersion, LTS, Flyway expand/contract
- [examples/](../../../examples/) — reference bundles
