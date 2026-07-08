# Historian SLA dashboard (BL-161)

Reference layout for **historian query SLO observability** — p50/p95 latency vs documented targets from `GET /api/v1/platform/analytics/historian-sla`.

| File | Purpose |
|------|---------|
| [dashboard-layout.json](dashboard-layout.json) | `layoutJson` for `root.platform.dashboards.historian-sla` |
| [bff-functions.example.json](bff-functions.example.json) | Script function sketch calling the SLA API |

## Quick start

1. Create dashboard object `root.platform.dashboards.historian-sla` and paste `dashboard-layout.json` as layout.
2. Deploy platform script functions from `bff-functions.example.json` (or wire widgets to existing admin BFFs).
3. Open admin console → dashboard **Historian SLA** (platform analytics API is open to authenticated users).

## Documented SLO (defaults)

| Query | Scope | Target (p95) |
|-------|-------|--------------|
| **Aggregate** | ≤ 1M points bucketed | **< 2 s** |
| **Raw trend** | ≤ 10k points | **< 500 ms** |

Defaults bind via `ispf.variable-history.slo` (`VariableHistorySloProperties`). Lab gate: `deploy/tools/historian-scale-benchmark.sh`.

See [docs/en/variable-history.md](../docs/en/variable-history.md) § Query SLO (BL-161) and [docs/en/historian-tiers.md](../docs/en/historian-tiers.md).
