# HMI offline field soak (BL-151 / Post-S33)

> **Status:** Lab — PWA offline validation. Hub: [doc-status.md](doc-status.md).

Field procedure for **operator PWA offline** (dashboards + mimics). CI gates (FPS, Lighthouse) are **Done**; this closes Post-S33 **2h / 8h soak evidence** on a real wall or tablet.

See [operator-pwa-android-smoke.md](operator-pwa-android-smoke.md) · [hmi-quality-gates.md](hmi-quality-gates.md).

---

## Soak tiers

| Tier | Duration | When |
| ---- | -------- | ---- |
| **CI evidence** | seconds | `npm run pwa:offline-evidence` — config/TTL check |
| **Field minimum** | **2 h** | Roadmap Wave 2 — video wall / mini-TEC |
| **Stretch** | **8 h** | Matches Workbox + localStorage TTL |

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
