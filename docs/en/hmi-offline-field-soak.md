# HMI offline field soak (BL-151 / Post-S33)

> **Status:** Lab — PWA offline validation. Hub: [doc-status.md](doc-status.md).

Field procedure for **operator PWA offline** (dashboards + mimics). CI gates (FPS, Lighthouse) are **Done**; this closes Post-S33 **2h / 8h soak evidence** on a real wall or tablet.

See [operator-pwa-android-smoke.md](operator-pwa-android-smoke.md) · [hmi-quality-gates.md](hmi-quality-gates.md).

---

## Soak tiers

| Tier | Duration | When |
| ---- | -------- | ---- |
| **CI evidence** | seconds | `npm run pwa:offline-evidence` — config/TTL check |
| **Lab automated** | **2 h** (CDP offline) | `npm run pwa:offline-field-soak` against demostand — shell/banner samples |
| **Field minimum** | **2 h** | Roadmap Wave 2 — tablet / video wall airplane mode |
| **Stretch** | **8 h** | Matches Workbox + localStorage TTL |

### Lab automated soak

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

Honesty: Playwright CDP `Network.offline` on the hosted operator UI — not on-site tablet airplane mode. Still proves no white-screen / shell loss over 2 h and reconnect. On-site tablet remains the field sign-off tier.

---

## Prerequisites

- Operator app deployed (`?mode=operator&app=<id>`)
- Chrome Android or kiosk with PWA install
- Network kill switch (airplane mode or VLAN isolation)

---

## Field procedure

1. Open operator app online — warm dashboards and mimics (navigate each screen once).
2. Enable airplane mode / disconnect VLAN.
3. Every 30 min: scroll dashboards, open mimic, verify stale/offline banner.
4. At 2 h (or 8 h): note any white screens or JS errors.
5. Reconnect — confirm values refresh within 5 min (`OperatorOfflineBanner` clears).

Log in [hmi-offline-soak-journal.template.md](../evidence/hmi-offline-soak-journal.template.md).

---

## CI baseline (integrator pre-check)

```bash
cd apps/web-console
npm ci && npm run build && npm run pwa:offline-evidence
```

Nightly: same command in `nightly.yml` `web-console-full` job.

Playwright: `e2e/pwa-android.spec.ts` (preview + SW registration; not a literal 2h soak).

---

## Sign-off

| Claim | Proof |
| ----- | ----- |
| BL-151 PWA Done | CI `pwa:offline-evidence` + preview e2e |
| Field 2h | Dated journal, no P0 UI loss |
| Field 8h | Journal + stretch tier complete |

---

## Related

| Doc | Purpose |
| --- | ------- |
| [roadmap § Phase 26](roadmap.md) | HMI excellence |
| [mes-field-pilot-playbook.md](mes-field-pilot-playbook.md) | Same stand, MES loop |
