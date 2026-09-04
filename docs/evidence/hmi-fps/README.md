# HMI live FPS evidence

| File | Site | Result |
| ---- | ---- | ------ |
| [2026-09-04-ispf-vps-0.9.207-hmi-stress-500.json](./2026-09-04-ispf-vps-0.9.207-hmi-stress-500.json) | ispf.iot-solutions.ru @ **0.9.207** | **`hmi-stress-500` 500 el** unmocked, median **~47 FPS**, soft floor 20; live Object WS present |
| [2026-09-04-ispf-vps-0.9.207-ui-pump-station.json](./2026-09-04-ispf-vps-0.9.207-ui-pump-station.json) | ispf.iot-solutions.ru @ **0.9.207** | `ui-pump-station` (~7 el), median **60 FPS**, **471** `VARIABLE_UPDATED` |
| [2026-08-31-ispf-vps-0.9.203-ui-pump-station.json](./2026-08-31-ispf-vps-0.9.203-ui-pump-station.json) | ispf.iot-solutions.ru @ **0.9.203** | `ui-pump-station` (~7 el), median **60 FPS**, **517** real `VARIABLE_UPDATED` WS events |
| [2026-08-30-ispf-vps-0.9.193-ui-pump-station.json](./2026-08-30-ispf-vps-0.9.193-ui-pump-station.json) | ispf.iot-solutions.ru @ **0.9.193** | `ui-pump-station` facility mimic (~7 el), median **60 FPS**, **479** real `VARIABLE_UPDATED` WS events during sample |

## How to re-run

Facility mimic (~7 el):

```bash
cd apps/web-console
E2E_BASE_URL=https://ispf.iot-solutions.ru \
E2E_LIVE_FPS=1 \
E2E_USERNAME=admin \
E2E_PASSWORD=admin \
E2E_OPERATOR_APP=ui-pump-station \
MIMIC_MIN_FPS_LIVE=30 \
E2E_LIVE_FPS_EVIDENCE=../../docs/evidence/hmi-fps/YYYY-MM-DD-ispf-vps-ui-pump-station.json \
npm run test:quality -- --grep "live operator mimic"
```

500-el unmocked (seed once, then measure):

```bash
ISPF_BASE_URL=https://ispf.iot-solutions.ru ISPF_PASSWORD=admin \
  bash tools/hmi/seed-stress-mimic-500.sh

cd apps/web-console
E2E_BASE_URL=https://ispf.iot-solutions.ru \
E2E_LIVE_FPS=1 \
E2E_USERNAME=admin \
E2E_PASSWORD=admin \
E2E_OPERATOR_APP=hmi-stress-500 \
MIMIC_MIN_FPS_LIVE=20 \
E2E_LIVE_FPS_EVIDENCE=../../docs/evidence/hmi-fps/YYYY-MM-DD-ispf-vps-hmi-stress-500.json \
npm run test:quality -- --grep "live operator mimic"
```

CI mocked 500-el stress remains the BL-152 acceptance gate (`MIMIC_MIN_FPS`, default 55). The `hmi-stress-500` archive is **unmocked demostand** evidence for large live Object WS rendering (scorecard HMI gap). Soft floor 20 FPS is intentional for the 500-el live path — do not claim Phase-26 60 FPS from that run.
