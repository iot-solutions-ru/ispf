# ADR-0003: Commercial bundle licensing

## Status

Accepted (2026-06-22)

> **Supersession (license of core):** platform core is **AGPL v3** with optional Enterprise dual-license — [0016-agpl-dual-licensing](0016-agpl-dual-licensing.md). This ADR still governs **bundle / driver-pack RSA binding**; replace “Apache core” below with “AGPL platform” when reading.

## Context

The ISPF platform ships under AGPL (community) without DRM on the core engines. Commercial solutions (bundle, driver pack) ship separately; we need verifiable binding to an installation without turning every deploy into a core license check.

## Decision

1. **Core** is not licensed; optional verification on `POST .../deploy` when the manifest contains a `license` section.
2. **Installation ID** — file `{data-dir}/.ispf-installation-id` (hex); generated on first start; sent to the vendor to issue a license.
3. **`license` format in bundle:** `bundleId`, `minPlatformVersion`, `installationId`, `contentSha256`, `expiresAt`, `signature` (RSA-SHA256 over canonical claims JSON).
4. **`contentSha256`** — SHA-256 of canonical JSON manifest **without** the `license` field.
5. **Public key** — in platform config (`ispf.license.public-key-pem`); private key — with vendor (`tools/license-builder/`).
6. **`ispf.license.enforce`:** `true` — invalid license → HTTP 403; `false` — WARN (local/dev).
7. Bundle **without** `license` — deploy unchanged (open reference apps).

## Consequences

- Implementation: `com.ispf.server.license.*`, [commercial-licensing](../commercial-licensing.md).
- [plugins](../plugins.md) — commercial bundle delivery requirements.

## Related

- [0001-app-platform-boundary](0001-app-platform-boundary.md)
- REQ-FW-10, FW-11 in [roadmap](../roadmap.md)
