# ADR-0036: Commercial bundle IP protection — balanced policy

## Status

Accepted (2026-07-07)

## Context

RSA license on manifest ([0003-commercial-bundle-licensing](0003-commercial-bundle-licensing.md)) binds **delivery** of a bundle to `installationId`, but after deploy configuration lives in the object tree ([0007-bundle-tree-packaging](0007-bundle-tree-packaging.md), [application-principles](../application-principles.md) P1). The installation administrator sees dashboards, mimics, functions, bindings and can theoretically reproduce the solution in parts or via export / pull-from-tree.

An explicit product policy is needed: how much to protect runtime configuration vs how much to allow site-specific customization.

## Decision

**Balanced model (soft IP protection).** Do not introduce hard DRM on the object tree.

### What we protect

| Layer | Mechanism | Goal |
|-------|-----------|------|
| **Delivery** | RSA `license` in manifest, `installationId`, `contentSha256` | Signed artifact cannot move to another server without a new license |
| **Commerce** | EULA, marketplace, activation code, entitlement | Legal and business control of resale |
| **Value** | Bundle updates, support, vertical content | A v1.0 copy without vendor path goes stale |

### What we do not do (by design)

- Do not block export / pull-from-tree / subtree export for licensed applications.
- Do not encrypt or obfuscate declarative JSON in the tree.
- Do not forbid customization of bundle objects on the customer installation (bindings, HMI, variables, workflows).
- Do not introduce «operator-only» hiding of configuration from integrator/admin — on-prem admin owns the server.

Full technical protection against copying declarative configuration is **unachievable and undesirable** for a tree-first platform: it breaks customization, audit, AI-assisted editing, and normal SCADA/MES integration practice.

### Optional (low priority, no enforce by default)

- **Provenance** — on deploy, tag objects with metadata (`bundleId`, `bundleVersion`) for audit and support, not for blocking.
- **UI hints** — installation ID and license error hints in the bundle panel (web-console).

Hard **export gate** (`restrict-bundle-export`) — **not planned** without a separate ADR and legal/commercial request.

## Consequences

- Commercial bundle vendor relies on: deploy-license + contract + updates, not a «black box» in the tree.
- Customer with admin access may copy and customize configuration **within their installation**; moving to another site without a new license remains an EULA violation, not a technically impossible action.
- Platform does not grow a DRM layer; focus stays on delivery and marketplace.

## Related

- [0003-commercial-bundle-licensing](0003-commercial-bundle-licensing.md) — RSA at deploy
- [0007-bundle-tree-packaging](0007-bundle-tree-packaging.md) — bundle = tree packaging
- [commercial-licensing](../commercial-licensing.md)
- [LICENSE-COMMERCIAL](../../LICENSE-COMMERCIAL.md)
