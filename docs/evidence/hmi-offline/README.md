# HMI offline evidence (BL-151 / Post-S33)

| File | Site | Result |
| ---- | ---- | ------ |
| [2026-09-04-ispf-vps-0.9.207-offline-2h.json](./2026-09-04-ispf-vps-0.9.207-offline-2h.json) | ispf.iot-solutions.ru @ **0.9.207** | Lab automated **2 h** CDP offline (`ui-pump-station`) — **PASS** (27 samples, reconnect OK) |
| [2026-09-04-ispf-vps-0.9.207-journal.md](./2026-09-04-ispf-vps-0.9.207-journal.md) | lab automated | Journal for 2h soak + CI reaffirm |
| [2026-08-25-ispf-vps-journal.md](./2026-08-25-ispf-vps-journal.md) | lab / CI | CI `pwa:offline-evidence` PASS (reaffirmed 2026-08-30) |

## How to re-run

CI policy (seconds):

```bash
cd apps/web-console && npm run pwa:offline-evidence
```

Lab automated 2 h (Playwright CDP offline):

```bash
cd apps/web-console
E2E_BASE_URL=https://ispf.iot-solutions.ru \
E2E_USERNAME=admin E2E_PASSWORD=admin \
E2E_OPERATOR_APP=ui-pump-station \
HMI_OFFLINE_SOAK_MINUTES=120 \
HMI_OFFLINE_SAMPLE_EVERY_SEC=300 \
HMI_OFFLINE_SOAK_EVIDENCE=../../docs/evidence/hmi-offline/YYYY-MM-DD-ispf-vps-offline-2h.json \
  npm run pwa:offline-field-soak
```

On-site tablet / airplane mode remains the field sign-off tier — see [hmi-offline-field-soak.md](../../en/hmi-offline-field-soak.md).
