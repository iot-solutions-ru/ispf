# ISPF Commercial License (template)

**Status:** legal template — requires review by qualified counsel before use with customers.

## Scope

This agreement grants the **Customer** a non-exclusive license to use the ISPF platform
and optional entitlements **without AGPL copyleft obligations** for the licensed scope, subject
to payment and the terms below.

This does **not** automatically cover third-party copyleft components inside optional
**driver packs** (GPL/LGPL/MPL) unless explicitly listed in the order form.

## Tiers

| Tier | Platform (AGPL exemption) | Driver packs | Application bundles |
|------|---------------------------|--------------|---------------------|
| **Community** | No — AGPL applies | Open packs per `licenseType` | Open reference bundles |
| **Enterprise** | Yes (`platform-license.json`) | Open packs | Customer / vendor bundles per their EULA |
| **Enterprise+** | Yes | + RSA-signed commercial driver packs | + vendor commercial bundles |

## Platform license file

File: `{ISPF_DATA_DIR}/platform-license.json`

```json
{
  "tier": "enterprise",
  "minPlatformVersion": "0.9.32",
  "installationId": "<from GET /api/v1/platform/installation-id>",
  "expiresAt": "2027-12-31T23:59:59Z",
  "signature": "<RSA-SHA256 over canonical payload>"
}
```

Signing payload fields (sorted JSON): `tier`, `minPlatformVersion`, `installationId`, `expiresAt`.

## Application bundles (separate product)

Commercial **application bundles** (objects, dashboards, widgets, functions) are licensed
**separately** from the platform. Deploy-time RSA license in bundle manifest — see
[COMMERCIAL_LICENSING.md](docs/COMMERCIAL_LICENSING.md).

Declarative bundle content is **not** platform source code; bundle EULA controls redistribution
of the bundle artifact.

## Related

- [LICENSE.md](docs/LICENSE.md)
- [COMMERCIAL_LICENSING.md](docs/COMMERCIAL_LICENSING.md)
- [LICENSED_DRIVER_PACKS.md](docs/LICENSED_DRIVER_PACKS.md)
