# HMI live FPS evidence

| File | Site | Result |
| ---- | ---- | ------ |
| [2026-08-31-ispf-vps-0.9.203-ui-pump-station.json](./2026-08-31-ispf-vps-0.9.203-ui-pump-station.json) | ispf.iot-solutions.ru @ **0.9.203** | `ui-pump-station` (~7 el), median **60 FPS**, **517** real `VARIABLE_UPDATED` WS events |
| [2026-08-30-ispf-vps-0.9.193-ui-pump-station.json](./2026-08-30-ispf-vps-0.9.193-ui-pump-station.json) | ispf.iot-solutions.ru @ **0.9.193** | `ui-pump-station` facility mimic (~7 el), median **60 FPS**, **479** real `VARIABLE_UPDATED` WS events during sample |

## How to re-run

```bash
cd apps/web-console
E2E_BASE_URL=https://ispf.iot-solutions.ru \
E2E_LIVE_FPS=1 \
E2E_USERNAME=admin \
E2E_PASSWORD=admin \
E2E_OPERATOR_APP=ui-pump-station \
MIMIC_MIN_FPS_LIVE=30 \
E2E_LIVE_FPS_EVIDENCE=../../docs/evidence/hmi-fps/YYYY-MM-DD-ispf-vps-<app>.json \
npm run test:quality -- --grep "live operator mimic"
```

CI mocked 500-el stress remains the BL-152 acceptance gate. This archive is **unmocked demostand** evidence for the live Object WS path (scorecard HMI gap). Do not invent a 500-el live claim from this run.
