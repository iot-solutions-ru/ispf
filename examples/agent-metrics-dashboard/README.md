# Agent metrics dashboard (BL-181)

Reference layout for **agent observability v2** — per-tool latency, error rate, and token totals from `GET /api/v1/ai/agent/metrics/tools`.

| File | Purpose |
|------|---------|
| [dashboard-layout.json](dashboard-layout.json) | `layoutJson` for `root.platform.dashboards.ai-ops` |
| [bff-functions.example.json](bff-functions.example.json) | Script function sketch calling the metrics API |

## Quick start

1. Create dashboard object `root.platform.dashboards.ai-ops` and paste `dashboard-layout.json` as layout.
2. Deploy platform script functions from `bff-functions.example.json` (or wire widgets to existing admin BFFs).
3. Open admin console → dashboard **AI Ops** (admin role required for metrics API).

See [docs/AI_AGENT.md](../docs/AI_AGENT.md) § AI tool metrics dashboard widget and [ADR-0034](../docs/decisions/0034-agent-observability-and-session-knowledge.md).
