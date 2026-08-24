# HMI offline field soak journal (BL-151 / Post-S33)

> Copy to `docs/evidence/hmi-offline/YYYY-MM-DD-<site>-journal.md`.

| Field | Value |
| ----- | ----- |
| Site | _video wall / mini-TEC / tablet_ |
| Operator app | _appId_ |
| Browser / PWA | _Chrome Android \| Edge kiosk_ |
| ISPF version | |

## Soak targets

| Tier | Duration | Pass criteria |
| ---- | -------- | ------------- |
| Minimum (Wave 2) | **2 h** offline | Dashboards/mimics render; stale banner; no white screen |
| Stretch | **8 h** offline | Matches Workbox + localStorage TTL policy |
| Reconnect | _5 min_ | `syncOperatorCachesOnReconnect` — values refresh |

## Session log

| Start (UTC) | End | Offline min | Dashboards OK | Mimics OK | Reconnect sync | Notes |
| ----------- | --- | ----------- | ------------- | --------- | -------------- | ----- |
| | | | | | | |

## CI baseline (lab)

```bash
cd apps/web-console && npm run build && npm run pwa:offline-evidence
```

**Status:** _ci-evidence-only \| field-2h \| field-8h_
