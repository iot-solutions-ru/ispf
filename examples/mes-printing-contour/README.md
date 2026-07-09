# MES Printing Contour (`mes-printing-contour`)

Operator walkthrough for the **docanima «Печать»** contour: stage list (SCR-01), ARM shell (SCR-00), job bag (SCR-02), materials (SCR-04), events (SCR-07), complete (SCR-08).

| File | Purpose |
|------|---------|
| `bundle.json` | Deploy via `POST /api/v1/applications/mes-printing-contour/deploy` |
| `generate_bundle.py` | Regenerate `bundle.json` and test resource copy |

## Prerequisites

- ISPF **0.9.106+** (`writeVariable` in script functions, BFF input schema fixes)
- Hub: `root.platform.devices.printing-contour-hub`
- Machine telemetry: `root.platform.devices.print-machine-pr120`

## Deploy

```bash
curl -s -X POST http://localhost:8080/api/v1/applications/mes-printing-contour/deploy \
  -H "Content-Type: application/json" \
  --data-binary @examples/mes-printing-contour/bundle.json
```

Operator UI: `?mode=operator&app=mes-printing-contour`

## Walkthrough (≤ 30 min)

| Step | docanima | Action |
|------|----------|--------|
| 1 | SCR-01 | Open **Stages** — report lists stages on PR120; new orders appear via scheduler (every 3 min while &lt;2 `PLANNED`) or **Создать заказ** |
| 2 | SCR-01 | Select `PRINT-OP-001`, click **В работу** (`mes_printing_startStage`) |
| 3 | SCR-00 | Open **ARM** — monitoring values, input/output roll previews |
| 4 | SCR-02 | Tab **Job bag** — view sections; edit and **Сохранить раздел** |
| 5 | SCR-04 | Tab **Materials** — scan input roll, write-off/return stub, machine stock report |
| 6 | SCR-07 | Tab **Events** — register downtime (`110` / `120`), view journal |
| 7 | SCR-08 | Tab **Complete** — readiness check, register output roll if needed, **Завершить этап** |

### Seed IDs (for API tests)

| Entity | UUID |
|--------|------|
| Planned stage `PRINT-OP-001` | `aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa` |
| Active demo `PRINT-OP-002` | `bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb` |
| Completed ref | `cccccccc-cccc-cccc-cccc-cccccccccccc` |

### BFF examples

```bash
# List stages
curl -s -X POST http://localhost:8080/api/v1/bff/invoke \
  -H "Content-Type: application/json" \
  -d '{"objectPath":"root.platform.devices.printing-contour-hub","functionName":"mes_printing_listStages","input":{"rows":[{"machineCode":"PR120"}]}}'

# Start planned stage
curl -s -X POST http://localhost:8080/api/v1/bff/invoke \
  -H "Content-Type: application/json" \
  -d '{"objectPath":"root.platform.devices.printing-contour-hub","functionName":"mes_printing_startStage","input":{"rows":[{"workAreaId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","startedBy":"operator"}]}}'

# Generate a new order + PLANNED stage (manual)
curl -s -X POST http://localhost:8080/api/v1/bff/invoke \
  -H "Content-Type: application/json" \
  -d '{"objectPath":"root.platform.devices.printing-contour-hub","functionName":"mes_printing_generateOrder","input":{"rows":[{"machineCode":"PR120","projectName":"Labels run","productName":"Flexo job","customerName":"Acme LLC"}]}}'
```

## Architecture notes

- **SQL** (`app_mes_printing_contour`): stages, rolls, job bag, events, ERP outbox — not mass nodes in the object tree.
- **Order feed**: `mes_printing_generateOrder` (manual SCR-01 form) and `mes_printing_generateOrderAuto` (scheduler `printing-order-feed`, every 3 min while &lt;2 `PLANNED` stages on PR120). Each order creates `mes_order` + `work_area` (`PLANNED`) + job bag sections + `erp_outbox` `ORDER_CREATED`.
- **Object tree**: hub BFF + `print-machine-pr120` telemetry (`status`, `speedMpm`, `activeWorkAreaId`).
- **1C / DWH**: `erp_outbox` stub (`STAGE_STARTED` / `STAGE_COMPLETED`); no live HTTP in v1.

## Anima operator UI (1:1 appearance)

Reference React ARM lives in **`Anima_front`** (separate repo/folder). ISPF is backend only via BFF:

| Item | Value |
|------|-------|
| Prod URL | https://ispf.iot-solutions.ru/operator-printing/ |
| Deploy | `.\examples\mes-printing-contour\deploy-anima-front.ps1` |
| Gateway env | `VITE_API_GATEWAY=ispf` |
| Wire | `POST /api/v1/bff/invoke` + `anima-operator-v1` |

Screens: SCR-01 production plan, SCR-00 order execution (monitoring). Sidebar SCR-02…08 — next wave.

## Related

- [mes-print-line](../mes-print-line/) — simpler print demo (unchanged)
- [mes-platform-production](../mes-platform-production/) — full MES certification bundle
