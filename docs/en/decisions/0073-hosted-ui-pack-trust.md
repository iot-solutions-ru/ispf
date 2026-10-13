# ADR-0073: Hosted ui-pack trust boundaries

## Status

**Accepted** (2026-10-13) — amends [0054](0054-hosted-ui-packs.md) with an explicit trust residual and hardening direction.  
**Wave 2A** (2026-10-13) ships CSP/COOP/CORP on `/apps/` and a BFF-scoped `uiPackAppId` token. Partner login-in-pack is **Partial** (Referer only).

## Context

Ui-packs are served under `/apps/<appId>/` on the console origin ([0054](0054-hosted-ui-packs.md)). Same-origin delivery is convenient for cookies/BFF but weakens browser isolation versus a separate origin. Bundle signing and SW denylist reduce some risk; CSP/COOP were called out in pen-test prep as open.

## Decision

### 1. Trust model (honest)

| Control | Status |
|---------|--------|
| Pack files from `ISPF_UI_PACKS_DIR` / marketplace install | **Done** |
| Service worker denylist for `/apps/` | **Done** |
| Bundle / pack signing when `require-signed-bundles` | **Partial** (platform paths) |
| **Separate origin** for untrusted partner packs | **Not claimed** |
| **CSP / COOP / CORP** defaults on `/apps/*` | **Done** — see §4. `style-src` still allows `'unsafe-inline'`. Script nonce/hash is not shipped. |
| **Scoped token** claim `uiPackAppId` | **Done** — see §5. Local/test mint plus OIDC claim enforcement. This server does not mint Keycloak tokens. |
| **Partner login-in-pack** | **Partial** — see §6. 403 only when `Referer` is `/apps/<appId>/` and trust is partner. Omitting `Referer` still reaches `/api/v1/auth/login`. |

### 2. Hard rules

1. Treat third-party ui-packs as **hostile HTML/JS**. CSP is on by default; it is not a certification.
2. **Partner packs must not log in inside the pack.** A console login token has the user’s full rights. Partner class uses a BFF-scoped token (`uiPackAppId`) instead. First-party packs (oil-control and other site-owned SPAs) may still call `/api/v1/auth/login`.
3. Industry apps built by the site owner remain valid; this ADR targets **marketplace trust**, not forbidding first-party SPAs.
4. Login inside a pack is not safer than console login. A scoped token is a narrower credential, not a safer password form.

### 3. Hardening order

1. Default CSP on `/apps/<appId>/` — **shipped** (conservative policy below). Follow-up: script nonce/hash and dropping `style-src 'unsafe-inline'`.
2. `uiPackTrust` on `ui-pack.json`: `firstParty` (default) | `partner` | `signedPartner` | `devOnly`. Partner class blocks in-pack login when `Referer` is present. **Partial.**
3. Longer-term: optional dedicated pack host / subdomain. **Not claimed.**

### 4. CSP (Wave 2A)

`HostedUiPackFilter` sets these headers on every `GET`/`HEAD` `/apps/<appId>/` response, including 404 and redirects:

```
Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'
Cross-Origin-Opener-Policy: same-origin
Cross-Origin-Resource-Policy: same-origin
X-Content-Type-Options: nosniff
```

`frame-ancestors 'none'` keeps the pack out of iframes (top-level navigation, as in 0054). Nginx in front of the JVM must not strip these headers.

### 5. Scoped token (Wave 2A)

Claim name: **`uiPackAppId`**.

When the claim is present (local session column, or OIDC access-token claim), `IspfAuthorizationRules.deniesPackScopedApi` rejects every `/api/**` and `/actuator/**` route except:

- `POST /api/v1/bff/**` for **that** app (a `Referer` of `/apps/<other>/` or header `X-ISPF-Ui-Pack-App` that disagrees with the claim is rejected; a missing Referer does not widen the token to admin APIs)
- `GET /api/v1/auth/me`
- `POST /api/v1/auth/logout`
- `GET /api/v1/info` and `/actuator/health`

Realm role `admin` does not lift this. `UiPackScopedAccessFilter` enforces it inside the security chain. An on-behalf delegation header cannot turn the token into a console principal.

**Mint (local/test token auth only):** admin `POST /api/v1/security/ui-pack-tokens` with `{ "username", "appId", "ttlHours"? }`. The session row stores `ui_pack_app_id`. On OIDC, the identity provider puts `uiPackAppId` on the access token; this endpoint returns 400.

Packs must not call `POST /api/v1/auth/login` to obtain a console token.

### 6. Partner class

`ui-pack.json` field `uiPackTrust` (fallback `trust`). Missing → `firstParty`.

| Value | In-pack `POST /api/v1/auth/login` |
|-------|-----------------------------------|
| `firstParty` (default), `devOnly` | Allowed (same as console login; token has the user’s roles) |
| `partner`, `signedPartner` | **Forbidden.** 403 when `Referer` is `/apps/<appId>/`. |

**Residual:** browsers let a page set `referrerPolicy: no-referrer`. That request is indistinguishable from the console and can still mint a full token. A separate origin is the real fix and is **not claimed**.

## Consequences

- Amends 0054 Consequences with this residual.
- Security scorecard 9+ still needs hired pen-test (G-01); CSP alone is not a cert.
- Wave 2A closes the CSP gap and the “scoped token cannot call admin APIs” gap. It does **not** close “a partner pack can never mint a full console token.”

## Related

- [0054-hosted-ui-packs](0054-hosted-ui-packs.md), [security](../security.md), [marketplace](../marketplace.md), [adr-tension-resolution](../adr-tension-resolution.md)
