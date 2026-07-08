# MES OEE reference bundle (BL-121)

Reference pattern for **Overall Equipment Effectiveness** on ISPF without custom Java in `ispf-server`.

| Artifact | Purpose |
|----------|---------|
| `bundle.json` | Deploy via `POST /api/v1/applications/mes-oee-reference/deploy` |

## OEE model

| Factor | Formula |
|--------|---------|
| Availability | `(planned − downtime) / planned` |
| Performance | `(ideal_cycle × total_units) / run_time_sec` (capped at 100%) |
| Quality | `good_units / total_units` |
| **OEE** | `Availability × Performance × Quality × 100` |

Demo seed: line **LINE-A01**, shift **Morning** — OEE ≈ **85%**.

## Quick start

```bash
curl -X POST http://localhost:8080/api/v1/applications/mes-oee-reference/deploy \
  -H "Content-Type: application/json" \
  --data-binary @examples/mes-oee-reference/bundle.json
```

```bash
curl -X POST http://localhost:8080/api/v1/bff/invoke \
  -H "Content-Type: application/json" \
  -d '{"objectPath":"root.platform.devices.demo-sensor-01","functionName":"mes_oee_listShifts","input":{"schema":{"name":"in","fields":[]},"rows":[{}]}}'
```

Walkthrough: [docs/en/reference-mes-oee-walkthrough.md](../../docs/en/reference-mes-oee-walkthrough.md).

## CI

`MesOeeReferenceBundleSmokeTest` uses `packages/ispf-server/src/test/resources/mes-oee-reference-bundle.json`.
