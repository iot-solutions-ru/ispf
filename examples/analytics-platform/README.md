# Analytics platform examples (BL-210)

Deployment walkthrough bundles for **AF-capable** analytics profiles from [ADR-0038](../../docs/en/decisions/0038-analytics-platform-architecture.md).

| Profile | Tags (history) | Example | Lab gate |
|---------|----------------|---------|----------|
| **Lab S** | &lt; 500 | Built-in lab bootstrap | JVM `@Tag("load")` tests |
| **Site M** | 500–5k | [site-m/](site-m/) | `historian-scale-benchmark.sh` + CH rollups |
| **Enterprise L** | 5k–50k+ | [enterprise-l/](enterprise-l/) | `analytics-scale-gate.sh` + 50k catalog + 1B CH rows |

Historian computation recipes (binding rules, OEE, tag chains): [docs/en/analytics-historian-cookbook.md](../../docs/en/analytics-historian-cookbook.md)

## Positioning

ISPF Phase 33 delivers an **AF-capable** analytics plane (derived tags, multi-tag query, event frames, catalog/lineage) — **not** full OSIsoft PI / PI Vision parity. See [analytics-platform-gaps.md](../../docs/en/analytics-platform-gaps.md).

## Related

- [analytics-platform-roadmap.md](../../docs/en/analytics-platform-roadmap.md) — BL-200…210
- [historian-tiers.md](../../docs/en/historian-tiers.md)
- [variable-history.md](../../docs/en/variable-history.md) § Analytics SLO
