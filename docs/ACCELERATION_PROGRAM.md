# Acceleration program (S19–S23)

8-недельная программа delivery после закрытия excellence-волны S01–S18.  
Источник спринтов: [ROADMAP.md § S19–S23](ROADMAP.md#sprint-s19--calibration-acceleration).

| | |
| --- | --- |
| **Старт** | 2026-07-05 |
| **Freeze до** | 2026-08-30 (8 недель) |
| **Program owner** | Platform lead |
| **Cadence** | Weekly scorecard (пт), sprint demo |

---

## S19 — Calibration (Done 2026-07-05)

| ID | Deliverable | Артефакт |
| -- | ----------- | -------- |
| S19-01 | Baseline метрик | [§ Baseline](#baseline-2026-07-05), `tools/acceleration/baseline-report.json` |
| S19-02 | Scorecard + cadence | [§ Scorecard](#weekly-scorecard) |
| S19-03 | Scope freeze | [§ Scope freeze](#scope-freeze-s20s23) |

Обновление baseline: `python tools/acceleration/collect-baseline.py` (GitHub Actions + локальные ссылки).

---

## Baseline (2026-07-05)

Зафиксировано на `main`, июль 2026. Цель acceleration — улучшать от этих точек, не смешивать с новым scope.

### CI / QA

| KPI | Baseline | Источник | Target (S20) |
| --- | -------- | -------- | ------------ |
| PR pipeline wall time | ~17 min avg (49 runs), unstable success | `tools/acceleration/collect-baseline.py` | ≤25 min (`pr-fast`) |
| CI success rate (14d) | **20.4%** (Jul 2026 sample) | `gh run list --workflow=ci.yml` | ≥95% |
| Flaky e2e (quarter) | 0 formal quarantine | Playwright in CI | Triage policy (S20-04) |
| Nightly load gate | Pass @ p99≤3000 ms | [load-test.yml](../.github/workflows/load-test.yml) | Keep green |
| Cluster ownership tests | Pass | [cluster-load-test.yml](../.github/workflows/cluster-load-test.yml) | Keep green |

### Backend scale (JVM gates)

| KPI | Baseline | Источник | Target |
| --- | -------- | -------- | ------ |
| `list_devices` p99 @ 150 concurrent | ≤2500 ms (local), ≤5000 ms (CI) | `ListDevicesLoadTest` | No regression |
| `events/fire` p99 | ≤3000 ms (nightly gate) | `EventFireLoadTest` | No regression |
| MQTT event journal sustained | ~1878 events/s (VPS 0.9.87) | [LOAD_TESTING.md](LOAD_TESTING.md), HF01 | No regression |
| Cluster scale factor (3/1 replica) | ≥1.8× | `deploy/cluster-scale-load-test.py` | Keep ≥1.8 |

### Frontend / HMI (S21 targets)

| KPI | Baseline | Источник | Target (S21) |
| --- | -------- | -------- | ------------ |
| Lighthouse Performance (operator) | Not gated | — | ≥80, CI gate |
| Lighthouse Accessibility | Not gated | — | ≥90 |
| SCADA mimic FPS (large diagram) | Not measured | — | ≥60 fps (BL-92) |
| JS bundle (main) | Not budgeted | `npm run build` | Budget + gate (BL-95) |
| a11y axe critical | Partial (BL-93) | Manual | 0 critical in CI |

### Federation (S22 targets)

| KPI | Baseline | Источник | Target (S22) |
| --- | -------- | -------- | ------------ |
| Store-forward | Done (BL-117) | S17 | — |
| Peer health SLO | Done (BL-118) | S17 | — |
| Selective subtree sync | ~55% (BL-119) | Partial API | RFC + MVP |
| Chaos recovery | Not automated | — | Green chaos suite (BL-120) |

### Semantic (S23 targets)

| KPI | Baseline | Источник | Target (S23) |
| --- | -------- | -------- | ------------ |
| Haystack query | Done (BL-101–103) | S05 | — |
| Brick inference | Done (BL-104) | S23 | Inspector hints |
| Time-to-first-dashboard (semantic) | Not measured | — | ≤5 min demo |

---

## Weekly scorecard

**Когда:** каждая пятница, 30 min.  
**Участники:** program owner, DevOps, QA lead, frontend lead, platform lead.

**Agenda**

1. KPI review (таблица ниже) — green / yellow / red
2. Demo спринта (если конец 2-недельного цикла)
3. Blockers и эскалации
4. Scope change requests (только через [change control](#change-control))
5. Обновить строку «Week N» в scorecard log

### KPI scorecard (template)

| KPI | Owner | Target | Current | Trend | Status |
| --- | ----- | ------ | ------- | ----- | ------ |
| PR CI duration | DevOps | ≤25 min | TBD | — | 🟡 |
| CI success rate 14d | QA | ≥95% | TBD | — | 🟡 |
| Load gates green | QA | 100% | TBD | — | 🟢 |
| Cluster scale ≥1.8× | Platform | ≥1.8 | 1.8+ | — | 🟢 |
| MQTT ingress events/s | Platform | ≥1800 | ~1878 | — | 🟢 |
| Lighthouse perf | Frontend | ≥80 | — | — | ⚪ |
| Mimic FPS | Frontend | ≥60 | — | — | ⚪ |
| Federation chaos | Platform | green | — | — | ⚪ |
| Semantic demo ≤5 min | Platform | ≤5 min | — | — | ⚪ |

Status: 🟢 on target · 🟡 at risk · 🔴 off track · ⚪ not started

### Scorecard log

| Week | Dates | Sprint | Notes |
| ---- | ----- | ------ | ----- |
| W1 | 2026-07-05 | S19 Done | Baseline frozen; scope S20–S23 locked |
| W2 | 2026-07-12 | S20 Done, S21 gates | pr-fast/nightly split; Lighthouse/axe/bundle/mimic FPS |
| W3 | 2026-07-19 | S20 | |
| W4 | 2026-07-26 | S20 / S21 | |
| W5 | 2026-08-02 | S21 | |
| W6 | 2026-08-09 | S21 / S22 | |
| W7 | 2026-08-16 | S22 | |
| W8 | 2026-08-23 | S22 / S23 | |
| W9 | 2026-08-30 | S23 close | Go/No-Go review |

---

## Scope freeze (S20–S23)

**Период:** 2026-07-05 → 2026-08-30.  
**Правило:** новые epic/features **не добавляются** без change control. Bugfix P0/P1 и ops — всегда allowed.

### In scope (locked)

| Sprint | Theme | Tasks |
| ------ | ----- | ----- |
| **S20** | Speed Engine | pr-fast / nightly-full, path jobs, caches, flaky triage, CI dashboard |
| **S21** | HMI Moat | BL-92 mimic perf, BL-93 a11y, BL-95 Lighthouse + bundle budget |
| **S22** | Edge Trust | BL-119 selective sync, BL-120 federation chaos + runbook |
| **S23** | Differentiation | BL-104 Brick inference, BL-105 semantic roundtrip + demo KPI |

### Explicitly out of scope (defer)

| Item | BL | Reason |
| ---- | -- | ------ |
| MES / OEE patterns | BL-121–124 | Post-acceleration |
| Multi-tenant isolation | BL-125–126 | Post-acceleration |
| Historian dual-write | BL-116 | Needs separate ADR |
| ClickHouse prod rollout | BL-114 | Ops-on-request, not sprint work |
| New driver packs / GPL drivers | — | Legal review track |

### Change control

1. Requester opens issue с label `acceleration-change`
2. Program owner + affected lead: impact on 8-week KPI?
3. Approve only if P0 security/production or swap equal SP out of locked scope
4. Log decision in scorecard WN notes + ROADMAP history

### Go/No-Go (end of S23)

| Criterion | Pass |
| --------- | ---- |
| CI duration | Within ±10% of S20 target OR pr-fast ≤25 min sustained 2 weeks |
| HMI | Lighthouse + mimic gates green 2 consecutive weeks |
| Federation | Chaos suite green on lab |
| Semantic | Demo dashboard from query ≤5 min, recorded |

---

## Related

- [ROADMAP.md](ROADMAP.md) — sprint registry S19–S23
- [TESTING.md](TESTING.md) — test commands
- [CI_FLAKY_TRIAGE.md](CI_FLAKY_TRIAGE.md) — flake policy (S20-04)
- [LOAD_TESTING.md](LOAD_TESTING.md) — throughput baselines
