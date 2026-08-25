# MES field evidence (Post-S33)

| File | Site | Result |
| ---- | ---- | ------ |
| [2026-08-25-ispf-vps-ga-smoke.json](./2026-08-25-ispf-vps-ga-smoke.json) | ispf.iot-solutions.ru | GA smoke **8/8** (listLines, OEE, dispatch, SPC, batch, ERP enqueue/poll, operator UI) |
| [2026-08-25-ispf-vps-journal.md](./2026-08-25-ispf-vps-journal.md) | ispf.iot-solutions.ru | Lab day-0 journal; 7-day plant loop still open |

Re-run:

```bash
ISPF_BASE_URL=https://ispf.iot-solutions.ru ISPF_DEPLOY_PASSWORD=… \
  bash tools/agent-regression/mes-platform-ga-smoke.sh
```
