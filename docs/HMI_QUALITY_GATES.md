# HMI quality gates (S21 / BL-92–95)

Operator HMI quality: Lighthouse, bundle budget, axe a11y, SCADA mimic FPS.

См. [ACCELERATION_PROGRAM.md](ACCELERATION_PROGRAM.md) · [ROADMAP § S21](ROADMAP.md#sprint-s21--hmi-moat).

## Gates

| Gate | Command | Target | CI |
| ---- | ------- | ------ | -- |
| Bundle budget | `npm run bundle:budget` | See `scripts/bundle-budget.json` | nightly |
| Lighthouse | `npm run lighthouse:ci` | login a11y ≥85; operator a11y ≥90 (`LH_MIN_ACCESSIBILITY_OPERATOR`) | nightly |
| axe critical | `npm run test:quality` | 0 critical | nightly |
| Mimic FPS | `npm run test:quality` | ≥55 fps @ 120 elements | nightly |

```bash
cd apps/web-console
npm run build
npm run bundle:budget
npm run lighthouse:ci
npm run test:quality
```

Env overrides: `LH_MIN_PERFORMANCE`, `LH_MIN_ACCESSIBILITY`, `LH_MIN_ACCESSIBILITY_OPERATOR`, `MIMIC_MIN_FPS`, `MIMIC_STRESS_ELEMENTS`.

## Known gaps (BL-93)

| Area | Status | Notes |
| ---- | ------ | ----- |
| Color contrast (axe) | Done | `--text-muted` tokens + dedicated contrast tests on login/operator |
| Keyboard mimic editor | Done | Escape close, arrow nudge, V/P/C tools, Del, Ctrl+Z/Y/S; dialog `role` + `aria-pressed` on tools |
| Screen reader labels | Done | `AlarmBarOverlay` — `role="alert"` + `aria-live="assertive"` per alarm |
| SCADA symbol library | Done | [SCADA_SYMBOL_LIBRARY.md](SCADA_SYMBOL_LIBRARY.md), `customSvg.test.ts` |
| Mimic 60 fps @ tank-farm | Done | CI stress proxy: 120 symbols @ ≥55 fps (`stressMimic.ts`); full tank-farm diagram same render path |
| Lighthouse operator dashboard | Done | `lighthouse-ci.mjs` audits `/?mode=operator&app=e2e-operator` with API mocks |

## Profiling

- Stress document builder: `e2e/fixtures/stressMimic.ts`
- Render path: `ScadaMimicCanvas` — memoized connections + filtered element lists
- Chrome Performance tab on `/?mode=operator` with pipeline/tank-farm mimic for manual traces

## Road to targets

| KPI | Baseline (Jul 2026) | S21 target |
| --- | ------------------- | ---------- |
| Lighthouse performance (login) | ~60–75 local | KPI only; set `LH_MIN_PERFORMANCE=75` to enforce |
| Lighthouse accessibility | ~94 (login/operator) | ≥90 |
| Mimic FPS (120 el) | ≥60 in CI | ≥55 gate |
| Entry JS bundle | budget enforced | no regression |
