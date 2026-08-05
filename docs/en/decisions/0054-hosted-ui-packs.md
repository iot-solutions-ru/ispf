# ADR-0054: Hosted UI packs for application solutions

## Status

**Accepted** (2026-08-05) — Runtime shipped: `DropInUiPackLoader`, `HostedUiPackFilter` (`/apps/<appId>/`), marketplace `artifactKind: ui-pack`, companion `uiPackSlug` on application install. Dogfood demo: `examples/marketplace-ui-pack-demo/`. Oil Control SPA (`oil-control-azs-web`) remains the first production acceptance candidate.

## Context

Marketplace already installs **application bundles** (SQL schema, BFF script functions, tree objects, `operatorUi` dashboards) and drop-in packs (`symbol-pack`, `analytics-pack`, workflow templates). Industry solutions with a **custom React SPA** still require a **second host** (static nginx + `/api` reverse proxy to ISPF).

Today (before this ADR):

- `operatorUi` configures the ISPF operator shell (dashboard paths).
- `operatorUi.spaNav` (when present) is **metadata** mapping SPA routes → BFF functions — it does not host or launch a SPA.
- Listing `artifactKind` had no value for static UI assets.

Partners could not deliver a full “front + BFF” solution as a single Marketplace / Solutions install without violating the app/platform boundary: domain UI must not be merged into `apps/web-console`, and domain Java must not enter `ispf-server`.

## Decision

Introduce a generic platform capability **hosted UI packs**, parallel to symbol/analytics DropIn packs:

1. New marketplace **`artifactKind: ui-pack`** (zip of static assets + `ui-pack.json`).
2. Install root: `ISPF_UI_PACKS_DIR/<appId>/` (versioned replace on upgrade).
3. HTTP: serve `GET /apps/<appId>/**` from `ispf-server` with SPA fallback to `index.html` (same origin as `/api`).
4. Operator launcher: **Open app UI** → `hostedUiUrl` (`/apps/<appId>/`) or bridge `externalSpaUrl`.
5. Optional listing field: `uiPackSlug` — free application install also downloads the companion ui-pack.
6. Optional bundle field: `operatorUi.uiPack = { packId, version, entry }` / `externalSpaUrl`.
7. Security: path sandbox, size limits (`ispf.ui-pack.max-zip-bytes`); **no** server-side JS execution of pack content.
8. Signing / `minIspfVersion` follow existing marketplace pack rules.

### Non-goals

- Embedding industry SPA source into `apps/web-console`.
- Industry-specific REST controllers in `ispf-server`.
- Replacing operator dashboard widgets — keep as fallback when a UI pack is absent.
- Running Node or arbitrary backend from the pack.

### Platform capability gate

This is a **generic packaging/serving primitive** (like symbol packs), not an Oil Control feature. Oil Control (or any app) is only an acceptance case.

## Consequences

- One-click / air-gap install can cover BFF + SPA under one catalog flow (application listing + `uiPackSlug`, or separate ui-pack listing).
- SPA authors use relative `/api/v1` and a Vite `base` under `/apps/<appId>/`.
- Docs and CI catalog validate learn `artifactKind: ui-pack` for local examples (not under `marketplace-catalog/` bundle-only folders).

### Risks

- Subpath base URL mistakes break asset loading — document pack contract clearly.
- Large SPA zips increase marketplace artifact size — enforce limits.
- Iframe embedding in operator shell needs CSP review; default to top-level navigation.

## Acceptance (dogfood)

- [x] Install local listing that deploys ui-pack (`POST /api/v1/marketplace/ui-packs/ui-pack-demo/install`).
- [x] SPA reachable at `https://<ispf-host>/apps/<appId>/`.
- [x] Login + API same-origin (`/api/v1/info` from demo page).
- [ ] Oil Control: publish `oil-control` bundle + ui-pack from `oil-control-azs-web` dist on marketplace.
- [x] Operator dashboards still work if ui-pack is missing.
- [x] `docs/en/marketplace.md` documents `ui-pack`; example under `examples/marketplace-ui-pack-demo/`.

## Related

- [0001-app-platform-boundary](0001-app-platform-boundary.md)
- [0002-dogfooding-gate](0002-dogfooding-gate.md)
- [0007-bundle-tree-packaging](0007-bundle-tree-packaging.md)
- [marketplace](../marketplace.md)
- [applications](../applications.md)
- [operator-apps](../operator-apps.md)
- Oil Control bundle PR: https://github.com/iot-solutions-ru/ispf/pull/64
