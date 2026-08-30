# HMI offline soak journal (BL-151 / Post-S33)

| Field | Value |
| ----- | ----- |
| Site | ispf.iot-solutions.ru (lab / CI baseline) |
| Operator app | any deployed operator app (e.g. `mes-platform-production`) |
| Browser / PWA | CI policy check (`npm run pwa:offline-evidence`) |
| ISPF version | platform **0.9.192** (demostand jar); web-console source policy reaffirmed 2026-08-30 |

## Soak targets

| Tier | Duration | Pass criteria | Status |
| ---- | -------- | ------------- | ------ |
| CI | seconds | `npm run pwa:offline-evidence` | **PASS** 2026-08-25; **reaffirm PASS** 2026-08-30 |
| Minimum (Wave 2) | **2 h** offline | Dashboards/mimics render; stale banner; no white screen | pending on-site |
| Stretch | **8 h** offline | Matches Workbox + localStorage TTL | pending on-site |
| Reconnect | ≤5 min | `syncOperatorCachesOnReconnect` | pending on-site |

## CI baseline (lab) — 2026-08-30 reaffirm

```text
PASS  SW caches /api/v1/dashboards/
PASS  SW caches /api/v1/mimics/
PASS  SW cache maxAgeSeconds = 8h
PASS  operator localStorage TTL = 8h
PASS  sync on reconnect helper exists
BL-151 evidence OK
```

Prior CI baseline: 2026-08-25 (same five checks).

## Session log (field)

| Start (UTC) | End | Offline min | Dashboards OK | Mimics OK | Reconnect sync | Notes |
| ----------- | --- | ----------- | ------------- | --------- | -------------- | ----- |
| _pending_ | | | | | | tablet / video wall |

**Status:** ci-evidence-only (2h / 8h / reconnect still require on-site tablet or kiosk)
