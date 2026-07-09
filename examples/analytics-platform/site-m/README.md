# Site M — analytics platform profile (Scenario B)

**Target:** 500–5k history-enabled tags, three-tier historian (hot PG + warm ClickHouse), in-process analytics engine with CH rollups.

## Topology

| Component | Setting |
|-----------|---------|
| Replicas | 2× `unified` + ClickHouse |
| Historian | `ISPF_VARIABLE_HISTORY_STORE=jdbc`, dual-write warm tier |
| Analytics engine | `ISPF_ANALYTICS_ENGINE_ENABLED=true` |
| Materializer | `ISPF_ANALYTICS_MATERIALIZER_ENABLED=true` on analytics-capable node |

## Walkthrough (≤1 day)

### 1. Start stack

```bash
# From repo root — enable warm tier + analytics (see deploy/docker-compose.analytics.yml)
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.analytics.yml up -d
```

Or VPS Site profile per [demostands.md](../../docs/en/demostands.md).

### 2. Seed historian devices

```bash
python deploy/setup-mqtt-historian-devices.py --devices 32 --base-url http://127.0.0.1:8080
```

### 3. Apply derived tags

1. Open Explorer → `root.platform.analytics` → template **rollingAvg**
2. Apply to 3–5 devices (or use `POST /api/v1/platform/analytics/templates/apply`)
3. Verify `GET /api/v1/platform/analytics/tags`

### 4. Run historian + analytics gates

```bash
bash deploy/tools/historian-scale-benchmark.sh
bash deploy/tools/analytics-scale-gate.sh
```

Reports: `build/historian-scale/scale-benchmark.md`, `build/analytics-scale/analytics-scale-gate.md`.

### 5. Multi-tag chart

Import dashboard from [examples/historian-sla-dashboard](../historian-sla-dashboard/) or wire Chart widget with `analyticsQueryTagsJson`.

## SLO targets (Site M)

| Gate | Target |
|------|--------|
| Single-tag aggregate (1M pts) | p95 &lt; 2 s ([BL-161](../../docs/en/variable-history.md)) |
| Multi-tag query (10×7d×1h) | p95 &lt; 3 s ([BL-210](../../docs/en/variable-history.md)) |
| Materializer lag | &lt; 5 min behind wall clock |

## Sign-off

Record gate reports and date in your lab journal. Historian scorecard moves toward **≥9.5** only after documented lab pass — see [competitive-scorecard.md](../../docs/en/competitive-scorecard.md).
