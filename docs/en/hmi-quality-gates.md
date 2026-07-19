# HMI quality gates (S21 / BL-92–95, BL-152)

> **Status:** Lab — Lighthouse, axe, FPS. Hub: [doc-status.md](doc-status.md).

Operator HMI quality: Lighthouse, bundle budget, axe a11y, SCADA mimic FPS.

See [acceleration-program](acceleration-program.md) · [roadmap § S21](roadmap.md).

## Gates

| Gate | Command | Target | CI |
| ---- | ------- | ------ | -- |
| Bundle budget | `npm run bundle:budget` | See `scripts/bundle-budget.json` | nightly |
| Lighthouse | `npm run lighthouse:ci` | login a11y ≥85; operator a11y ≥90 (`LH_MIN_ACCESSIBILITY_OPERATOR`); ≥95 = ops stretch (not BL-152 blocker) | nightly |
| axe critical | `npm run test:quality` | 0 critical | nightly |
| Mimic FPS (stress) | `npm run test:quality` | ≥55 fps @ 500 el — **BL-152 Done acceptance** | nightly |
| Mimic FPS (WS update path) | `npm run test:quality` | WS floor (`MIMIC_MIN_FPS_WS`, default 35) while pumping `VARIABLE_UPDATED` | nightly |
| Mimic FPS (unmocked live) | `E2E_LIVE_FPS=1` + creds | Optional evidence only — **not** claimed ≥60 without a dated run | on-demand |

```bash
cd apps/web-console
npm run build
npm run bundle:budget
npm run lighthouse:ci
npm run test:quality
```

Env overrides: `LH_MIN_PERFORMANCE`, `LH_MIN_ACCESSIBILITY`, `LH_MIN_ACCESSIBILITY_OPERATOR`, `MIMIC_MIN_FPS`, `MIMIC_STRESS_ELEMENTS`.

### Mimic stress thresholds (BL-152)

| Profile | Elements | FPS floor | Env |
| ------- | -------- | --------- | --- |
| CI gate (BL-152 **Done**) | 500 | ≥55 | default `MIMIC_STRESS_ELEMENTS=500`, `MIMIC_MIN_FPS=55` |
| Stretch (ops, not acceptance) | 500 | ≥60 | `MIMIC_MIN_FPS=60` + unmocked `E2E_LIVE_FPS=1` when evidence exists |
| Legacy S21 proxy | 120 | ≥55 | `MIMIC_STRESS_ELEMENTS=120` |
| Tank-farm manual | full diagram | ≥60 | Chrome Performance on operator mimic |

Stress document builder: `e2e/fixtures/stressMimic.ts`. Playwright measures min FPS over two 2s windows (`e2e/quality-gates.spec.ts`).

## Known gaps (BL-93)

| Area | Status | Notes |
| ---- | ------ | ----- |
| Color contrast (axe) | Done | `--text-muted` tokens + dedicated contrast tests on login/operator |
| Keyboard mimic editor | Done | Escape close, arrow navigate between elements, Shift+arrow nudge, V/P/C tools, Del, Ctrl+Z/Y/S; dialog `role` + `aria-pressed` on tools |
| Screen reader labels | Done | `AlarmBarOverlay` — `role="alert"` + `aria-live="assertive"` per alarm |
| SCADA symbol library | Done | [scada-symbol-library](scada-symbol-library.md), `customSvg.test.ts` |
| Mimic 60 fps @ tank-farm | Done | CI stress proxy: 120 symbols @ ≥55 fps (`stressMimic.ts`); full tank-farm diagram same render path |
| Mimic FPS @ 500 el (WS update path) | Done | CI pumps `ispf-object-ws-message` VARIABLE_UPDATED; floor `MIMIC_MIN_FPS_WS` (default 35) |
| Mimic FPS @ 500 el (unmocked live API) | Ops note | Suite present; no dated ≥60 evidence — do not claim |
| Lighthouse operator dashboard | Done (CI) | a11y floor 90; ≥95 ops stretch only |

## Profiling

- Stress document builder: `e2e/fixtures/stressMimic.ts`
- Render path: `ScadaMimicCanvas` — memoized connections + filtered element lists
- Chrome Performance tab on `/?mode=operator` with pipeline/tank-farm mimic for manual traces

## Road to targets

| KPI | Baseline (Jul 2026) | S21 target | BL-152 acceptance |
| --- | ------------------- | ---------- | ------------------ |
| Lighthouse performance (login) | ~60–75 local | KPI only; set `LH_MIN_PERFORMANCE=75` to enforce | — |
| Lighthouse accessibility | ~94 (login/operator) | ≥90 | CI ≥90; ≥95 ops stretch |
| Mimic FPS (120 el) | ≥60 in CI | ≥55 gate | — |
| Mimic FPS (500 el) | e2e mocked operator | ≥55 CI (Done) | stretch ≥60 unmocked = ops |
| Entry JS bundle | budget enforced | no regression | no regression |
