# ADR-0054: Hosted UI packs for application solutions

## Status

**Proposed** (2026-08-05) — Docs / design gate. No implementation in this change. Dogfood acceptance candidate: marketplace app `oil-control`.

## Context

Marketplace already installs **application bundles** (SQL schema, BFF script functions, tree objects, `operatorUi` dashboards) and drop-in packs (`symbol-pack`, `analytics-pack`, workflow templates). Industry solutions with a **custom React SPA** still require a **second host** (static nginx + `/api` reverse proxy to ISPF).

Today:

- `operatorUi` configures the ISPF operator shell (dashboard paths).
- `operatorUi.spaNav` (when present) is **metadata** mapping SPA routes ↔ BFF functions — it does not host or launch a SPA.
- Listing `artifactKind` has no value for static UI assets.

Partners cannot deliver a full “front + BFF” solution as a single Marketplace / Solutions install. That weakens air-gap and dogfood stories without violating the app/platform boundary: domain UI must not be merged into `apps/web-console`, and domain Java must not enter `ispf-server`.

## Decision

Introduce a generic platform capability **hosted UI packs**, parallel to symbol/analytics DropIn packs:

1. New marketplace **`artifactKind: ui-pack`** (zip of static assets + pack manifest).
2. Install root: `ISPF_UI_PACKS_DIR/<appId>/` (versioned replace on upgrade).
3. HTTP: serve `GET /apps/<appId>/**` from `ispf-server` with SPA fallback to `index.html` (same origin as `/api`).
4. Operator launcher: deep-link / action “Open UI” → `/apps/<appId>/`.
5. Optional bundle field: `operatorUi.uiPack = { packId, version, entry }`.
6. Security: path sandbox, size limits, CSP; **no** server-side JS execution of pack content.
7. Signing / `minIspfVersion` follow existing marketplace pack rules.

### Optional bridge (A1)

Until UI packs ship, allow `operatorUi.externalSpaUrl` (or listing field) so the launcher can open an externally hosted SPA. Prefer A2 (hosted pack) for production dogfood.

### Non-goals

- Embedding industry SPA source into `apps/web-console`.
- Industry-specific REST controllers in `ispf-server`.
- Replacing operator dashboard widgets — keep as fallback when a UI pack is absent.
- Running Node or arbitrary backend from the pack.

### Platform capability gate

This is a **generic REQ-PF-style** packaging/serving primitive (like symbol packs), not an Oil Control feature. Oil Control (or any app) is only the first acceptance case.

## Consequences

- One-click / air-gap install can cover BFF + SPA under one catalog listing (or composite install of bundle + ui-pack).
- SPA authors use relative `/api/v1` and a Vite `base` under `/apps/<appId>/`.
- Docs and CI catalog validate must learn `artifactKind: ui-pack`.
- Roadmap needs an explicit BL before implementation (quality-over-features policy).

### Risks

- Subpath base URL mistakes break asset loading — document pack contract clearly.
- Large SPA zips increase marketplace artifact size — enforce limits.
- Iframe embedding in operator shell needs CSP review; default to top-level navigation.

## Acceptance (dogfood)

- Install listing that deploys application bundle + ui-pack from System → Solutions.
- SPA reachable at `https://<ispf-host>/apps/<appId>/`.
- Login + `POST /api/v1/bff/invoke` work same-origin.
- UI pack version upgrade without manual nginx deploy.
- Operator dashboards still work if ui-pack is missing.
- `docs/en/marketplace.md` documents `ui-pack`; example listing under `examples/`.

## Related

- [0001-app-platform-boundary](0001-app-platform-boundary.md)
- [0002-dogfooding-gate](0002-dogfooding-gate.md)
- [0007-bundle-tree-packaging](0007-bundle-tree-packaging.md)
- [marketplace](../marketplace.md)
- [applications](../applications.md)
- [operator-apps](../operator-apps.md)
- [plugins](../plugins.md)
- Oil Control bundle PR: https://github.com/iot-solutions-ru/ispf/pull/64
