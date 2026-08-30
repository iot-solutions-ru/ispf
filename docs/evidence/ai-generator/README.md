# Dated live-generator evidence archives (Post-S33).

| File | Site | Result |
| ---- | ---- | ------ |
| [2026-08-30-ispf-vps-0.9.191-hvac.json](./2026-08-30-ispf-vps-0.9.191-hvac.json) | ispf.iot-solutions.ru @ **0.9.191** | `functionalOk=true`, `softBudgetMet=true`, **18.7s**, signed |
| [2026-08-30-ispf-vps-0.9.191-mes.json](./2026-08-30-ispf-vps-0.9.191-mes.json) | ispf.iot-solutions.ru @ **0.9.191** | `functionalOk=true`, `softBudgetMet=true`, **19.4s**, signed |
| [2026-08-30-ispf-vps-0.9.191-scada.json](./2026-08-30-ispf-vps-0.9.191-scada.json) | ispf.iot-solutions.ru @ **0.9.191** | `functionalOk=true`, `softBudgetMet=true`, **19.3s**, signed |
| [2026-08-30-ispf-vps-0.9.191-journal.md](./2026-08-30-ispf-vps-0.9.191-journal.md) | ispf.iot-solutions.ru | Named-site journal — all three domains on 0.9.191 |
| [2026-08-30-ispf-vps-0.9.192-deploy.md](./2026-08-30-ispf-vps-0.9.192-deploy.md) | ispf.iot-solutions.ru | Deploy **0.9.192** + AI apply smoke signed |
| [2026-08-24-ispf-vps-hvac.json](./2026-08-24-ispf-vps-hvac.json) | ispf.iot-solutions.ru @ 0.9.186 | `functionalOk=true`, `softBudgetMet=true`, **19.6s**, Qwen3.6-35B |
| [2026-08-24-ispf-vps-mes.json](./2026-08-24-ispf-vps-mes.json) | ispf.iot-solutions.ru @ 0.9.186 | `functionalOk=true`, `softBudgetMet=true`, **19.1s**, MES domain |
| [2026-08-24-ispf-vps-scada.json](./2026-08-24-ispf-vps-scada.json) | ispf.iot-solutions.ru @ 0.9.186 | `functionalOk=true`, `softBudgetMet=true`, **19.5s**, SCADA domain |
| [2026-08-24-ispf-vps-journal.md](./2026-08-24-ispf-vps-journal.md) | ispf.iot-solutions.ru | Named-site journal — all three domains (0.9.186) |

Add new runs with `bash tools/agent-regression/run-vps-field-soak.sh <domain>` — do not commit API keys.
