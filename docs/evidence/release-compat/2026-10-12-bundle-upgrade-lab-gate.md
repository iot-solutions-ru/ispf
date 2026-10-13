# Bundle upgrade compatibility — lab gate (ADR-0064 Wave 2)

Date: 2026-10-12  
Scope: **lab CI / unit + MockMvc** — not cluster image N→N+1, not customer plant.

## What ran

| Check | Location |
|-------|----------|
| Accept / reject `formatVersion` | `BundleFormatVersionTest` |
| Catalog pilot validate + unsupported reject + install/redeploy | `BundleUpgradeCompatGateTest` |
| Pilot artifact | `examples/marketplace-catalog/marketplace-demo/bundle.json` (`formatVersion: 1`) |

## Honesty

- PASS here means the **current** platform build accepts format `1`, rejects unsupported formats, and can install then reinstall the marketplace-demo local bundle.
- It does **not** mean rolling upgrade of a multi-node cluster across platform releases, zero downtime, or FIELD maturity for any driver.

See [release-upgrade](../../en/release-upgrade.md).
