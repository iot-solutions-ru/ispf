> **Language:** Canonical English. Russian edition: [ru/reference-mes-oee-walkthrough.md](../ru/reference-mes-oee-walkthrough.md).

# MES OEE reference walkthrough (BL-121)

End-to-end reference scenario for ISPF solution developers: **line shift → OEE KPI (Availability × Performance × Quality) → downtime registration**. No custom Java in `ispf-server`.

Artifacts: [examples/mes-oee-reference/](../../examples/mes-oee-reference/), bundle `appId` = `mes-oee-reference`.

## Domain (simplified OEE)

| Entity | Description |
|----------|----------|
| **Shift** (`mes_oee_shift`) | Line, shift label, planned/downtime (min), ideal cycle (s), output total/good |
| **BFF hub** | Object path `root.platform.devices.demo-sensor-01` — functions and Operator UI event journal |
| **Hub device** | `root.platform.devices.mes-oee-hub-01` — created from `objects[]` in bundle (optional) |

### OEE formulas (in SQL `mes_oee_getKpi`)

| Factor | Formula |
|--------|---------|
| **Availability** | `(planned − downtime) / planned × 100` |
| **Performance** | `(ideal_cycle_sec × total_units) / run_time_sec × 100`, cap 100% |
| **Quality** | `good_units / total_units × 100` |
| **OEE** | `(planned − downtime) / planned × min(1, ideal×total / run_sec) × good / total × 100` |

Demo seed **LINE-A01 / Morning**: planned 480 min, downtime 45, ideal 12 s, total 2100, good 2050 → **OEE ≈ 85%**.

## Scenario steps

| # | Action | Object path / API | Effect | Operator |
|---|----------|-------------------|--------|----------|
| 1 | Deploy bundle | `POST /api/v1/applications/mes-oee-reference/deploy` | schema `app_mes_oee`, migration, functions | Admin |
| 2 | List shifts | BFF `mes_oee_listShifts` @ `demo-sensor-01` | SQL read, 1 seed shift | Operator UI |
| 3 | KPI per shift | BFF `mes_oee_getKpi` + `shiftId` (UUID) | OEE and A/P/Q components | Dashboard / form |
| 4 | Add downtime | BFF `mes_oee_addDowntime` + `minutes` | `downtime_minutes += minutes` (cap = planned) | Operator confirm |
| 5 | Repeat KPI | `mes_oee_getKpi` | OEE drops after downtime | Trend / alarm |

## REQ-PF mapping

| Mechanism | Usage in OEE reference |
|----------|-------------------------------|
| **PF-01** bundle deploy | `migrations`, `functions`, `operatorUi` |
| **PF-05** objects[] | `mes-oee-hub-01` device |
| **PF-02** BFF invoke | `mes_oee_listShifts`, `mes_oee_getKpi`, `mes_oee_addDowntime` |
| **Operator UI** | `appId` = `mes-oee-reference`, event journal @ `demo-sensor-01` |

## Readiness criteria

- [x] Bundle deploys on `test` profile without custom Java
- [x] `mes_oee_listShifts` returns seed shift LINE-A01
- [x] `mes_oee_getKpi` returns `oeePct` > 80 for seed data
- [x] `mes_oee_addDowntime` increases downtime
- [x] CI: `MesOeeReferenceBundleSmokeTest`

## Smoke commands

```bash
./gradlew :packages:ispf-server:test --tests "com.ispf.server.application.MesOeeReferenceBundleSmokeTest"
```

Locally (server running):

```bash
curl -s -X POST http://localhost:8080/api/v1/applications/mes-oee-reference/deploy \
  -H "Content-Type: application/json" \
  --data-binary @examples/mes-oee-reference/bundle.json

curl -s -X POST http://localhost:8080/api/v1/bff/invoke \
  -H "Content-Type: application/json" \
  -d '{"objectPath":"root.platform.devices.demo-sensor-01","functionName":"mes_oee_listShifts","input":{"schema":{"name":"in","fields":[]},"rows":[{}]}}'

curl -s -X POST http://localhost:8080/api/v1/bff/invoke \
  -H "Content-Type: application/json" \
  -d '{"objectPath":"root.platform.devices.demo-sensor-01","functionName":"mes_oee_getKpi","input":{"schema":{"name":"in","fields":[{"name":"shiftId","type":"STRING"}]},"rows":[{"shiftId":"dddddddd-dddd-dddd-dddd-dddddddddddd"}]}}'
```

## Related documents

- [reference-mes-walkthrough](reference-mes-walkthrough.md) — dispatch / tank reference
- [solution-developer-guide](solution-developer-guide.md)
- [applications](applications.md)
