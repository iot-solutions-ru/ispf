# ADR-0003: Commercial bundle licensing

## Status

**Accepted** (2026-06-22) — governs **bundle / driver-pack RSA binding** only.  
Platform core license is **AGPL v3** (+ optional Enterprise dual-license): [0016-agpl-dual-licensing](0016-agpl-dual-licensing.md) (**0016b**).

## Context

The ISPF platform ships under AGPL without DRM on core engines. Commercial solutions (bundle, driver pack) ship separately; we need verifiable binding to an installation without turning every deploy into a core license check.

## Decision

1. **Platform core** is AGPL (not “Apache”); optional **bundle** verification on `POST …/deploy` when the manifest contains a `license` section.
2. **Installation ID** — file `{data-dir}/.ispf-installation-id` (hex); generated on first start; sent to the vendor to issue a license.
3. **`license` format in bundle:** `bundleId`, `minPlatformVersion`, `installationId`, `contentSha256`, `expiresAt`, `signature` (RSA-SHA256 over canonical claims JSON).
4. **`contentSha256`** — SHA-256 of canonical JSON manifest **without** the `license` field.
5. **Public key** — in platform config (`ispf.license.public-key-pem`); private key — with vendor (`tools/license-builder/`).
6. **`ispf.license.enforce`:** `true` — invalid license → HTTP 403; `false` — WARN (local/dev).
7. Bundle **without** `license` — deploy unchanged (open reference apps).

### Honesty — Done / Partial / Gap

| Claim | Status | Notes |
|-------|--------|-------|
| RSA verify on deploy when `license` present | **Done** | `com.ispf.server.license.*` |
| Open bundles without `license` | **Done** | |
| Time-bound `expiresAt` | **Done** | Enforce rejects expired signatures |
| Remote **revoke** / CRL / kill-switch after issue | **Gap** | Not in product; ops revoke = rotate vendor key + re-issue + redeploy |
| Vendor **public-key rotation** protocol in-product | **Gap** | Config swap of `public-key-pem` only; no dual-key window like [0065](0065-secrets-key-rotation.md) |
| Core AGPL ≠ pack license | **Done** | Documented; pack RSA does not replace AGPL obligations |

Do **not** claim DRM, floating seats, or online license server from this ADR.

## Consequences

- Implementation: `com.ispf.server.license.*`, [commercial-licensing](../commercial-licensing.md).
- [plugins](../plugins.md) — commercial bundle delivery requirements.
- Follow-up (named tender): revoke list or dual public-key window — new ADR or amend here with fitness test.

## Related

- [0001-app-platform-boundary](0001-app-platform-boundary.md)
- [0016-agpl-dual-licensing](0016-agpl-dual-licensing.md) (**0016b**)
- REQ-FW-10, FW-11 in [roadmap](../roadmap.md)
