# HMI quality gates (S21 / BL-92–95)

Operator HMI quality: Lighthouse, bundle budget, axe a11y, SCADA mimic FPS.

См. [ACCELERATION_PROGRAM.md](ACCELERATION_PROGRAM.md) · [ROADMAP § S21](ROADMAP.md#sprint-s21--hmi-moat).

## Gates

| Gate | Command | Target | CI |
| ---- | ------- | ------ | -- |
| Bundle budget | `npm run bundle:budget` | See `scripts/bundle-budget.json` | nightly |
| Lighthouse | `npm run lighthouse:ci` | perf ≥75, a11y ≥85 (→80/90) | nightly |
| axe critical | `npm run test:quality` | 0 critical | nightly |
| Mimic FPS | `npm run test:quality` | ≥55 fps @ 120 elements | nightly |

```bash
cd apps/web-console
npm run build
npm run bundle:budget
npm run lighthouse:ci
npm run test:quality
```

Env overrides: `LH_MIN_PERFORMANCE`, `LH_MIN_ACCESSIBILITY`, `MIMIC_MIN_FPS`, `MIMIC_STRESS_ELEMENTS`.

## Known gaps (BL-93, BL-94)

| Area | Status | Notes |
| ---- | ------ | ----- |
| Color contrast (axe) | Partial | Rule disabled in CI baseline; audit manually for WCAG AA |
| Keyboard mimic editor | Partial | Runtime pan/zoom OK; full editor a11y backlog |
| Screen reader labels | Partial | Dynamic values need `aria-live` on alarm bar |
| SCADA symbol library | Partial | ISA pack imported; custom SVG upload docs incomplete |
| Mimic 60 fps @ tank-farm | In progress | Stress harness @ 120 `rect` symbols; tank-farm full diagram TBD |
| Lighthouse operator dashboard | Partial | Gate runs on login shell; operator with data in nightly e2e |

## Profiling

- Stress document builder: `e2e/fixtures/stressMimic.ts`
- Render path: `ScadaMimicCanvas` — memoized connections + filtered element lists
- Chrome Performance tab on `/?mode=operator` with pipeline/tank-farm mimic for manual traces

## Road to targets

| KPI | Baseline (Jul 2026) | S21 target |
| --- | ------------------- | ---------- |
| Lighthouse performance | ~75 (login) | ≥80 |
| Lighthouse accessibility | ~85 (login) | ≥90 |
| Mimic FPS (120 el) | measured in CI | ≥55 → 60 |
| Entry JS bundle | budget enforced | no regression |
