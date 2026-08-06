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

### Edge / nginx (mandatory for split deploy)

Production often serves `web-console` as **static files** from nginx (`root /opt/ispf/web-console`) and only proxies `/api/` and `/ws/` to `ispf-server`. In that layout, a catch-all:

```nginx
location / { try_files $uri $uri/ /index.html; }
```

will answer `GET /apps/<appId>/` with the **admin console** SPA (title “ISPF Admin Console”), even when the ui-pack is correctly installed under `ISPF_UI_PACKS_DIR/<appId>/` and Java serves it on `:8080`.

**Required:** proxy hosted UI packs to the JVM (same as `/api/`), before the SPA fallback. Templates: [`deploy/nginx-ispf.conf`](../../deploy/nginx-ispf.conf), [`deploy/nginx-vps-ssl.conf`](../../deploy/nginx-vps-ssl.conf):

```nginx
location ^~ /apps/ {
    proxy_pass http://127.0.0.1:8080;
    # … Host / X-Forwarded-* / Authorization as for /api/
}
```

Smoke after every nginx or platform deploy:

```bash
curl -fsS "https://<host>/apps/<appId>/" | grep -q '<app title or ui-pack marker>'
# Must NOT contain: <title>ISPF Admin Console</title>
curl -fsS -o /dev/null -w '%{http_code}\n' "http://127.0.0.1:8080/apps/<appId>/"   # expect 200 when pack installed
```

All-in-one JAR (UI embedded, no separate nginx static root) does not need this location — `HostedUiPackFilter` handles `/apps/` on the same port.

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

- **Nginx SPA fallback steals `/apps/`** on split VPS deploy — always keep `location ^~ /apps/` → JVM (see above). Symptom: public URL shows Admin Console; `curl :8080/apps/...` shows the real pack.
- Subpath base URL mistakes break asset loading — document pack contract clearly.
- Large SPA zips increase marketplace artifact size — enforce limits.
- Iframe embedding in operator shell needs CSP review; default to top-level navigation.
- Catalog `installed` for ui-pack must key off **`appId`** (install directory), not listing `packId` / slug when they differ (e.g. slug `oil-control-ui`, appId `oil-control`).

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
