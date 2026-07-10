# HMI quality gates (S21 / BL-92–95, BL-152)

Operator HMI quality: Lighthouse, bundle budget, axe a11y, SCADA mimic FPS.

See [acceleration-program](acceleration-program.md) · [roadmap § S21](roadmap.md).

## Gates

| Gate | Command | Target | CI |
| ---- | ------- | ------ | -- |
| Bundle budget | `npm run bundle:budget` | See `scripts/bundle-budget.json` | nightly |
| Lighthouse | `npm run lighthouse:ci` | login a11y ≥85; operator a11y ≥90 (`LH_MIN_ACCESSIBILITY_OPERATOR`) | nightly |
| axe critical | `npm run test:quality` | 0 critical | nightly |
| Mimic FPS (stress) | `npm run test:quality` | ≥55 fps @ 500 elements (BL-152 CI floor) | nightly |
| Mimic FPS (e2e default) | `quality-gates.spec.ts` | `MIMIC_MIN_FPS` defaults to **60** @ 500 el on **mocked** operator API | nightly when gate runs |

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
| CI gate (BL-152) | 500 | ≥55 | default `MIMIC_STRESS_ELEMENTS=500`, `MIMIC_MIN_FPS=55` |
| Phase 26 target | 500 | ≥60 | `MIMIC_STRESS_ELEMENTS=500`, `MIMIC_MIN_FPS=60` |
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
| Mimic FPS @ 500 el (live WebSocket) | Gap | e2e uses mocked API; `quality-gates.spec.ts` defaults `MIMIC_MIN_FPS=60` |
| Lighthouse operator dashboard | Done | `lighthouse-ci.mjs` audits `/?mode=operator&app=e2e-operator` with API mocks |

## Profiling

- Stress document builder: `e2e/fixtures/stressMimic.ts`
- Render path: `ScadaMimicCanvas` — memoized connections + filtered element lists
- Chrome Performance tab on `/?mode=operator` with pipeline/tank-farm mimic for manual traces

## Road to targets

| KPI | Baseline (Jul 2026) | S21 target | BL-152 target |
| --- | ------------------- | ---------- | ------------- |
| Lighthouse performance (login) | ~60–75 local | KPI only; set `LH_MIN_PERFORMANCE=75` to enforce | — |
| Lighthouse accessibility | ~94 (login/operator) | ≥90 | operator ≥95 (roadmap.md) |
| Mimic FPS (120 el) | ≥60 in CI | ≥55 gate | — |
| Mimic FPS (500 el) | e2e mocked operator | default ≥60 in spec; live WebSocket path not CI-gated |
| Entry JS bundle | budget enforced | no regression | no regression |
