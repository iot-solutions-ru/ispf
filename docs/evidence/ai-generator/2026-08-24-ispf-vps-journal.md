# AI generator field soak journal (BL-180)

| Field | Value |
| ----- | ----- |
| Site | ispf.iot-solutions.ru (VPS demostand / `ispf-vps`) |
| Domain | hvac + mes + scada (three dated oneshots) |
| Date (UTC) | 2026-08-24 |
| Integrator | cloud agent / Post-S33 |
| ISPF version | 0.9.186 |
| Model | Qwen/Qwen3.6-35B-A3B (`available: true`) |
| Evidence JSON | [hvac](./2026-08-24-ispf-vps-hvac.json) · [mes](./2026-08-24-ispf-vps-mes.json) · [scada](./2026-08-24-ispf-vps-scada.json) |

## Daily log

| Day | functionalOk | softBudgetMet | elapsedMs | Operator spot-check | Notes |
| --- | ------------ | ------------- | --------- | --------------------- | ----- |
| 1 (HVAC) | true | true | ~19600 | UI HTTP 200 | `run-vps-field-soak.sh hvac` |
| 1 (MES) | true | true | ~19100 | UI HTTP 200 | generator MES domain |
| 1 (SCADA) | true | true | 19467 | UI HTTP 200 | app `describe-a-scada-plant-with-one-pump-jfgk` |

## Sign-off

- [x] `functionalOk: true`, hub/dashboard/alert paths present in evidence JSON
- [x] Operator app opens (`/api/v1/operator-apps/<appId>/ui` → 200)
- [x] `softBudgetMet: true` (all domains ≪ 15 min soft budget)
- [x] No manual tree edits required after generator apply

**Status:** field-evidence-attached (named site oneshot; multi-day plant soak still optional)
