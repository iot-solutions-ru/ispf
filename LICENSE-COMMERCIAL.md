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

### Post-deploy configuration and IP (balanced policy)

After a licensed bundle is deployed, its configuration lives in the customer’s **object tree**
(dashboards, mimics, functions, bindings, etc.). The platform’s RSA deploy license ([ADR-0036](docs/decisions/0036-bundle-ip-balanced-protection.md))
binds the **signed manifest artifact** to a specific `installationId`; it does **not** encrypt or
lock down individual tree objects on the customer’s server.

**Intended for counsel to reflect in bundle EULA:**

| Permitted (typical) | Restricted / controlled |
|---------------------|-------------------------|
| Use and **customize** deployed configuration on the **licensed installation** (site-specific HMI, bindings, variables) | **Redeploy** the same signed bundle or equivalent manifest on another `installationId` without a new license |
| Export/pull configuration for **backup, migration within the same licensed estate**, or integration work **as contract allows** | **Redistribution**, resale, or publication of the vendor’s bundle content as a competing product |
| Receive **updates** from the vendor via marketplace or support channel | Circumventing deploy-time license verification |

On-prem administrators with full access can theoretically reconstruct declarative configuration
manually; **complete technical prevention of copying is neither claimed nor pursued** — control
relies on deploy licensing, contract terms, and ongoing updates/support, not DRM on the object tree.

## Related

- [LICENSE.md](docs/LICENSE.md)
- [COMMERCIAL_LICENSING.md](docs/COMMERCIAL_LICENSING.md)
- [LICENSED_DRIVER_PACKS.md](docs/LICENSED_DRIVER_PACKS.md)
- [ADR-0036 bundle IP policy](docs/decisions/0036-bundle-ip-balanced-protection.md)
