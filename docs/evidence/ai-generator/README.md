# Dated live-generator evidence archives (Post-S33).

| File | Site | Result |
| ---- | ---- | ------ |
| [2026-08-24-ispf-vps-hvac.json](./2026-08-24-ispf-vps-hvac.json) | ispf.iot-solutions.ru | `functionalOk=true`, `softBudgetMet=true`, **19.6s**, Qwen3.6-35B |
| [2026-08-24-ispf-vps-mes.json](./2026-08-24-ispf-vps-mes.json) | ispf.iot-solutions.ru | `functionalOk=true`, `softBudgetMet=true`, **19.1s**, MES domain |
| [2026-08-24-ispf-vps-scada.json](./2026-08-24-ispf-vps-scada.json) | ispf.iot-solutions.ru | `functionalOk=true`, `softBudgetMet=true`, **19.5s**, SCADA domain |
| [2026-08-24-ispf-vps-journal.md](./2026-08-24-ispf-vps-journal.md) | ispf.iot-solutions.ru | Named-site journal — all three domains |

Add new runs with `bash tools/agent-regression/run-vps-field-soak.sh <domain>` — do not commit API keys.