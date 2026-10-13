# ADR-0073: Hosted ui-pack trust boundaries

## Status

**Accepted** (2026-10-13) — amends [0054](0054-hosted-ui-packs.md) with an explicit trust residual and hardening direction.

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
| **CSP / COOP / CORP** defaults on `/apps/*` | **Partial** — `HostedUiPackFilter` sets CSP + COOP/CORP (Wave 2A); nginx may still add `unsafe-inline` styles |
| **Pack-scoped token** (`ROLE_UI_PACK`) | **Partial** — `UiPackScopedAccessFilter` denies admin APIs; BFF-only allowlist. Mint UX / partner login ban remain follow-up |

### 2. Hard rules

1. Treat third-party ui-packs as **hostile HTML/JS** until CSP+signing policy is enforced for that pack class.
2. Pack login UX must not be documented as safer than console login — token privilege is the user’s.
3. Industry apps built by the site owner (oil-control, etc.) remain valid; this ADR targets **marketplace trust**, not forbidding first-party SPAs.

### 3. Hardening order (implementation follow-up)

1. Default CSP on `/apps/<appId>/` (script-src nonce or hash; no blanket `unsafe-inline` in prod profile).
2. Optional `uiPackTrust: firstParty | signedPartner | devOnly` in listing/manifest.
3. Longer-term: optional dedicated pack host / subdomain.

## Consequences

- Amends 0054 Consequences with this residual.
- Security scorecard 9+ still needs hired pen-test (G-01); CSP alone is not a cert.

## Related

- [0054-hosted-ui-packs](0054-hosted-ui-packs.md), [security](../security.md), [marketplace](../marketplace.md)
