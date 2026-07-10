> **Язык:** русская версия (вычитка). Канонический английский: [en/acceleration-program.md](../en/acceleration-program.md).

# Программа ускорения (S19–S23)

Восьминедельный delivery после закрытия excellence-волны S01–S18.  
Источник спринтов: [roadmap.md § S19–S23](roadmap.md).

| | |
| --- | --- |
| **Старт** | 2026-07-05 |
| **Freeze до** | 2026-08-30 (8 недель) |
| **Владелец программы** | Platform lead |
| **Каденс** | Еженедельный scorecard (пт), demo спринта |

---

## S19 — Калибровка (готово 2026-07-05)

| ID | Результат | Артефакт |
| -- | ----------- | -------- |
| S19-01 | Baseline метрик | [§ Baseline](#baseline-2026-07-05), `tools/acceleration/baseline-report.json` |
| S19-02 | Scorecard + каденс | [§ Scorecard](#weekly-scorecard) |
| S19-03 | Scope freeze | [§ Scope freeze](#scope-freeze-s20s23) |

Обновление baseline: `python tools/acceleration/collect-baseline.py` (GitHub Actions + локально).

---

## Baseline (2026-07-05)

Зафиксировано на `main`, июль 2026. Цель acceleration — улучшать от этих точек, не смешивая с новым scope.

### CI / QA

| KPI | Baseline | Источник | Цель (S20) |
| --- | -------- | -------- | ------------ |
| Wall time PR pipeline | ~17 мин в среднем (49 runs), нестабильный success | `tools/acceleration/collect-baseline.py` | ≤25 мин (`pr-fast`) |
| CI success rate (14d) | **20.4%** (выборка июль 2026) | `gh run list --workflow=ci.yml` | ≥95% |
| Flaky e2e (квартал) | 0 формального карантина | Playwright в CI | Политика triage (S20-04) |
| Nightly load gate | Pass @ p99≤3000 ms | [load-test.yml](../.github/workflows/load-test.yml) | Держать зелёным |
| Cluster ownership tests | Pass | [cluster-load-test.yml](../.github/workflows/cluster-load-test.yml) | Держать зелёным |

### Backend scale (JVM gates)

| KPI | Baseline | Источник | Цель |
| --- | -------- | -------- | ------ |
| `list_devices` p99 @ 150 concurrent | ≤2500 ms (local), ≤5000 ms (CI) | `ListDevicesLoadTest` | Без регрессии |
| `events/fire` p99 | ≤3000 ms (nightly gate) | `EventFireLoadTest` | Без регрессии |
| MQTT event journal sustained | ~1878 events/s (VPS 0.9.87) | [load-testing](load-testing.md), HF01 | Без регрессии |
| Cluster scale factor (3/1 replica) | ≥1.8× | `deploy/cluster-scale-load-test.py` | Держать ≥1.8 |

### Frontend / HMI (цели S21)

| KPI | Baseline | Источник | Цель (S21) |
| --- | -------- | -------- | ------------ |
| Lighthouse Performance (operator) | Не в gate | — | ≥80, CI gate |
| Lighthouse Accessibility | Не в gate | — | ≥90 |
| SCADA mimic FPS (большая схема) | Не измерялось | — | ≥60 fps (BL-92) |
| JS bundle (main) | Без budget | `npm run build` | Budget + gate (BL-95) |
| a11y axe critical | Частично (BL-93) | Вручную | 0 critical в CI |

### Federation (цели S22)

| KPI | Baseline | Источник | Цель (S22) |
| --- | -------- | -------- | ------------ |
| Store-forward | Готово (BL-117) | S17 | — |
| Peer health SLO | Готово (BL-118) | S17 | — |
| Selective subtree sync | ~55% (BL-119) | Partial API | RFC + MVP |
| Chaos recovery | Не автоматизировано | — | Зелёный chaos suite (BL-120) |

### Semantic (цели S23)

| KPI | Baseline | Источник | Цель (S23) |
| --- | -------- | -------- | ------------ |
| Haystack query | Готово (BL-101–103) | S05 | — |
| Brick inference | Готово (BL-104) | S23 | Подсказки в Inspector |
| Time-to-first-dashboard (semantic) | Не измерялось | — | ≤5 min demo |

---

## Еженедельный scorecard

**Когда:** каждая пятница, 30 мин.  
**Участники:** владелец программы, DevOps, QA lead, frontend lead, platform lead.

**Повестка**

1. Обзор KPI (таблица ниже) — green / yellow / red
2. Demo спринта (если конец двухнедельного цикла)
3. Блокеры и эскалации
4. Запросы на изменение scope (только через [change control](#change-control))
5. Обновить строку «Week N» в scorecard log

### KPI scorecard (шаблон)

| KPI | Владелец | Цель | Текущее | Тренд | Статус |
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

Статус: 🟢 on target · 🟡 at risk · 🔴 off track · ⚪ not started

### Scorecard log

| Week | Даты | Sprint | Заметки |
| ---- | ----- | ------ | ----- |
| W1 | 2026-07-05 | S19 Done | Baseline заморожен; scope S20–S23 зафиксирован |
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
**Правило:** новые epic/features **не добавляются** без change control. Bugfix P0/P1 и ops — всегда разрешены.

### In scope (заблокировано)

| Sprint | Тема | Задачи |
| ------ | ----- | ----- |
| **S20** | Speed Engine | pr-fast / nightly-full, path jobs, caches, flaky triage, CI dashboard |
| **S21** | HMI Moat | BL-92 mimic perf, BL-93 a11y, BL-95 Lighthouse + bundle budget |
| **S22** | Edge Trust | BL-119 selective sync, BL-120 federation chaos + runbook |
| **S23** | Differentiation | BL-104 Brick inference, BL-105 semantic roundtrip + demo KPI |

### Explicitly out of scope (отложено)

| Пункт | BL | Причина |
| ---- | -- | ------ |
| MES / OEE patterns | BL-121–124 | Post-acceleration |
| Multi-tenant isolation | BL-125–126 | Post-acceleration |
| Historian dual-write | BL-116 | Отдельное ADR |
| ClickHouse prod rollout | BL-114 | Ops по запросу, не sprint work |
| Новые driver packs / GPL drivers | — | Legal review track |

### Change control

1. Инициатор открывает issue с label `acceleration-change`
2. Владелец программы + затронутый lead: влияние на 8-недельный KPI?
3. Одобрять только при P0 security/production или swap равного SP из locked scope
4. Зафиксировать решение в scorecard WN notes + история roadmap

### Go/No-Go (конец S23)

| Критерий | Pass |
| --------- | ---- |
| CI duration | В пределах ±10% от цели S20 ИЛИ pr-fast ≤25 min две недели подряд |
| HMI | Lighthouse + mimic gates зелёные 2 недели подряд |
| Federation | Chaos suite зелёный в lab |
| Semantic | Demo dashboard из query ≤5 min, записано |

---

## Связанные документы

- [roadmap](roadmap.md) — реестр спринтов S19–S23
- [testing](testing.md) — команды тестов
- [ci-flaky-triage](ci-flaky-triage.md) — политика flaky (S20-04)
- [load-testing](load-testing.md) — throughput baselines
