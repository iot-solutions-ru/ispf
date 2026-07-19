> **Language:** Canonical English. Russian: use the same tags in `docs/ru` hubs.

# Documentation status tags

Every hub-listed page should carry an honest **Status** so newcomers know what is product-ready vs lab vs draft.

| Tag | Meaning |
|-----|---------|
| **Stable** | Accurate for current `main`; safe for OSS try / solution building |
| **Beta** | Works, but known gaps or maturity caveats (see scorecard / page banner) |
| **Draft** | Design / target; APIs or portals may be stub — do not treat as GA |
| **Charter** | Roadmap / planning document; not a how-to |
| **Lab** | Stress / training / interop runbooks; not product GA |
| **Internal** | Maintainers, rights-holders, dogfood — not a public product entry |

## Where tags appear

1. Column **Status** in [readme.md](readme.md) full catalog.  
2. Optional one-liner under the H1 on the page itself:  
   `> **Status:** Stable — …`  
3. Stronger banners already used for Draft/Partial (partner program) take precedence; marketplace is Stable (BL-183 Done).

## Maintenance

When promoting a feature from stub → GA, update the hub Status and any page banner in the same PR as the code. Re-pin [competitive-scorecard](competitive-scorecard.md) after a full code audit.
