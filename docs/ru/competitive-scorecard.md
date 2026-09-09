> **Язык:** русская версия (вычитка). Канонический английский: [en/competitive-scorecard.md](../en/competitive-scorecard.md).

# Конкурентный scorecard ISPF (BL-189)

> **Статус:** Stable — Готовность по коду. Теги: [doc-status](../en/doc-status.md).

Матрица публичной готовности для Phases 25–33 (фазы 25–33).

**Версии (не смешивать):**

| Pin | Смысл |
|-----|--------|
| **0.9.207** | **Полный competitive audit 2026-09-09** — оценки и evidence ниже. Lab evidence учтены; field/OT/MES honesty сохранена. |
| **0.9.102** | Предыдущий полный code audit (июль 2026). Исторический baseline; заменён этим аудитом. |
| **0.9.206** | Предыдущий demostand pin. **Не** scored matrix. |

**Актуальный столбец:** **Проверено по коду** — доказательства из `main`, тестов и датированных lab-архивов на pin **0.9.207** (2026-09-09).

Шкала **1–10** относительно лучших в классе: Ignition, Kepware/KEPServerEX, OSIsoft PI, Siemens Opcenter, Tulip, зрелые IIoT-платформы с контекстным деревом.

| # | Измерение | Baseline (фаза 24) | **Проверено по коду (0.9.207)** | Цель | Фаза / BL |
|---|-----------|:-------------------:|:-------------------------------:|:------:|------------|
| 1 | Единая модель данных (дерево объектов) | 9.0 | **8.5** | **10** | [roadmap](roadmap.md#этап-25--ot-trust), [roadmap](roadmap.md#этап-29--платформа-mes), [roadmap](roadmap.md#этап-30--глубина-автоматизации) — BL-164…168, BL-173 |
| 2 | SCADA / HMI / мнемосхемы | 7.0 | **8.0** | **10** | [roadmap](roadmap.md#этап-26--совершенство-hmi) — BL-146…152 |
| 3 | OT/IT connectivity (drivers.md) | 6.0 | **7.0** | **10** | [roadmap](roadmap.md#этап-25--ot-trust) — BL-140…145; BL-191 honesty Done |
| 4 | Historian / time-series | 7.0 | **9.5** | **10** | [roadmap](roadmap.md#фаза-28--историк-в-масштабе) — BL-159…163; [roadmap](roadmap.md#фаза-33--аналитическая-платформа-af-capable) БЛ-200…210 |
| 5 | Automation / alarms | 7.5 | **7.5** | **10** | [roadmap](roadmap.md#этап-27--безопасность-предприятия), [roadmap](roadmap.md#этап-30--глубина-автоматизации) — BL-153…157, BL-171…174 |
| 6 | Workflow / BPMN | 6.5 | **7.5** | **10** | [roadmap](roadmap.md#этап-30--глубина-автоматизации) — BL-176 |
| 7 | MES / ISA-95 | 5.5 | **6.5** | **10** | [roadmap](roadmap.md#этап-29--платформа-mes) — BL-164…170 |
| 8 | Скорость low-code | 8.0 | **8.0** | **10** | [roadmap](roadmap.md#этап-26--совершенство-hmi), [roadmap](roadmap.md#фаза-31--автопилот-ии) — BL-146…152, BL-177…180 |
| 9 | AI-assisted разработка | 9.0 | **9.0** | **10** | [roadmap](roadmap.md#фаза-31--автопилот-ии) — BL-177…182 |
| 10 | Security / RBAC / мультитенантность | 6.5 | **8.5** | **10** | [roadmap](roadmap.md#этап-27--безопасность-предприятия) — BL-153…157 |
| 11 | Deploy / scale / edge | 8.0 | **7.5** | **10** | [roadmap](roadmap.md#этап-25--ot-trust), [roadmap](roadmap.md#фаза-28--историк-в-масштабе), [roadmap](roadmap.md#этап-32--экосистема-и-рынок) — BL-144…145, BL-186…187 |
| 12 | Экосистема / маркетплейс | 4.0 | **6.5** | **10** | [roadmap](roadmap.md#этап-32--экосистема-и-рынок) — BL-183…185 |
| 13 | Documentation / DX | 9.0 | **8.5** | **10** | [roadmap](roadmap.md#этап-32--экосистема-и-рынок) — BL-189, BL-190 |
| 14 | Stack modernity | 9.0 | **9.5** | **10** | maintain — Spring Boot 4, React 19, NATS, опция ClickHouse |

**Итого (проверено по коду): ~8.0/10** — простое среднее по 14 измерениям (сумма 112.0 / 14).

### Audit 2026-09-09 — delta vs 0.9.102

| # | Измерение | 0.9.102 | **0.9.207** | Почему |
|---|-----------|:-------:|:-----------:|--------|
| 2 | SCADA / HMI | 7.5 | **8.0** | Live FPS + unmocked 500-el; lab CDP **2h + 8h** (**P-HMI-8H**). Не 9+: **P-HMI-FIELD** parked. |
| 4 | Historian | 7.0 | **9.5** | Enterprise L **Lab PASS** 2026-09-05. Honesty: synthetic CH fill; demostand — маленький сайт. |
| 10 | Security / RBAC | 8.0 | **8.5** | Trusted-channel + PostgreSQL RLS. Не 9+: **G-01** pen-test open; WebAuthn parked. |
| 11 | Deploy / scale / edge | 7.0 | **7.5** | Fold Phase 32 Helm/ARM/MoM. |
| 12 | Ecosystem / marketplace | 5.0 | **6.5** | Fold Marketplace GA + CI catalog gate. |
| 3 | OT/IT | 7.0 | **7.0** | Lab pilots in progress — **не** field Done / не OT 10/10. |

Program wave 8 (~9.8) по-прежнему **отозвана**.

---

## Definition of done (10/10 overall)

Из [roadmap](roadmap.md) (Фазы 25–33 / DoD). Статус на **0.9.207**:

| Критерий | Статус |
|----------|--------|
| Все **14 измерений ≥9.5**, ни одно ≤8 (BL-189) | **Не выполнено** — среднее ~8.0; остаются dims ≤8 (OT, alarms, BPMN, MES, deploy, ecosystem) |
| Agent regression **≥95% green** с live LLM (BL-178) | **Выполнено** — полный live suite **52/52 @100%**. Nightly CI — режим **platform**. |
| Marketplace GA checklist complete (BL-183) | **Выполнено** — Partner Portal external |
| Competitive scorecard published per release (BL-189) | **Выполнено** — этот документ |

---

## Доказательства аудита кода (0.9.207)

Классы доказательств: **REAL** (runtime + тесты / датированный lab), **PARTIAL** (ядро работает, известные пробелы), **STUB** (явная заглушка в коде).

| # | Измерение | Оценка | Вердикт | Ключевые доказательства |
|---|-----------|:-----:|---------|-------------------------|
| 1 | Unified data model | 8.5 | **REAL** | Object tree, CEL, blueprints; correlators в `EventCorrelatorService` |
| 2 | SCADA / HMI | 8.0 | **PARTIAL** | 218 PID symbols; demostand FPS + 500-el unmocked; lab offline 2h/8h CDP. Остаток: on-site tablet (**P-HMI-FIELD**) |
| 3 | OT/IT drivers | 7.0 | **PARTIAL** | Catalog 162/162 lab; BL-140 pilots in progress ([ot-trust](../evidence/ot-trust/)); lab ≠ plant; DNP3 write open |
| 4 | Historian | 9.5 | **REAL (lab SLO)** | Enterprise L lab 2026-09-05 ([evidence](../evidence/historian-scale/2026-09-05-lab-192.168.100.10-enterprise-l.md)). Honesty: synthetic CH fill |
| 5 | Automation / alarms | 7.5 | **PARTIAL** | Alert rules + correlators; shelving persisted (BL-158) |
| 6 | Workflow / BPMN | 7.5 | **REAL** | SubProcess/Message tests; не полная BPMN 2.0; **P-BPMN** parked |
| 7 | MES / ISA-95 | 6.5 | **PARTIAL** | Marketplace MES; ERP stub **`simulated`**; **P-ERP** parked |
| 8 | Low-code velocity | 8.0 | **REAL** | Dashboard builder, bundle deploy, spreadsheets |
| 9 | AI-assisted dev | 9.0 | **REAL** | BL-178 52/52; soft evidence не поднимает оценку |
| 10 | Security / RBAC | 8.5 | **PARTIAL** | MFA/ACL/RLS Done; **G-01** pen-test open; WebAuthn parked |
| 11 | Deploy / scale / edge | 7.5 | **PARTIAL** | MoM + Helm + ARM; нет CI cluster load proof |
| 12 | Ecosystem / marketplace | 6.5 | **PARTIAL** | Marketplace GA; Partner Portal sync external |
| 13 | Documentation / DX | 8.5 | **REAL** | Docs + ADR + evidence folders |
| 14 | Stack modernity | 9.5 | **REAL** | Spring Boot 4, React 19, ClickHouse option |

### Известные проблемы целостности кода (исправить до повышения OT-оценки)

| Проблема | Где |
|----------|-----|
| DNP3 poll-only (BETA) — `writePoint` не реализован | `Dnp3DeviceDriver.writePoint()` |
| Partner Portal external | portal sync вне repo |
| OT field pilots incomplete | [ot-trust](../evidence/ot-trust/) — lab soak ≠ customer sign-off |

---

## Разрыв до цели

1. **OT drivers (7.0 → 9+):** field pilot sign-off + DNP3 write; lab ≠ OT 10/10.
2. **ERP L4 / MES (6.5 → 9+):** live 1C/SAP (**BL-169** / **P-ERP**); production MES sites.
3. **AI (9.0 → 10):** optional multi-day plant journal.
4. **Ecosystem (6.5 → 9+):** Partner Portal + external partner catalog.
5. **Historian (9.5 → 10):** non-synthetic / customer L ops honesty.
6. **HMI (8.0 → 9+):** on-site tablet (**P-HMI-FIELD**); ≥60 FPS @ 500-el.
7. **Security (8.5 → 9+):** hired pen-test + retest (**G-01**); WebAuthn if needed. Internal engineering review ≠ cert.
8. **Compliance:** tender pack docs Done; без заявления о сертификации продукта.

---

## История program wave (отгруженные артефакты)

Исторические **program**-оценки отражали скорость delivery BL; это **не** конкурентная готовность.

<details>
<summary>Столбцы wave 1–8 (заменены «проверено по коду»)</summary>

| # | Измерение | W1 | W2 | W3 | W4 | W5 | W6 | W7 | W8 (program) |
|---|-----------|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:------------:|
| 1 | Unified data model | 9.2 | 9.3 | 9.4 | 9.5 | 9.6 | 9.7 | 9.8 | 9.9 |
| 2 | SCADA / HMI | 7.8 | 7.9 | 8.0 | 8.5 | 8.6 | 9.0 | 9.3 | 9.6 |
| 3 | OT/IT connectivity | 6.8 | 6.9 | 7.0 | 8.5 | 8.7 | 9.3 | 9.5 | 9.7 |
| 4 | Historian | 7.5 | 7.6 | 7.7 | 8.5 | 8.6 | 9.0 | 9.3 | 9.6 |
| 5 | Automation / alarms | 8.0 | 8.1 | 8.2 | 8.6 | 8.7 | 9.1 | 9.4 | 9.6 |
| 6 | Workflow / BPMN | 7.0 | 7.1 | 7.2 | 8.5 | 8.6 | 9.0 | 9.3 | 9.6 |
| 7 | MES / ISA-95 | 6.2 | 6.4 | 6.6 | 8.5 | 8.8 | 9.5 | 9.6 | 9.7 |
| 8 | Low-code velocity | 8.5 | 8.6 | 8.7 | 8.8 | 9.0 | 9.5 | 9.6 | 9.7 |
| 9 | AI-assisted development | 9.4 | 9.6 | 9.7 | 9.8 | 9.85 | 9.95 | 9.97 | 9.98 |
| 10 | Security / RBAC | 7.2 | 7.3 | 7.4 | 8.0 | 8.8 | 9.3 | 9.5 | 9.7 |
| 11 | Deploy / scale / edge | 8.3 | 8.6 | 9.0 | 9.2 | 9.3 | 9.5 | 9.6 | 9.7 |
| 12 | Ecosystem / marketplace | 5.0 | 5.5 | 6.0 | 8.5 | 8.7 | 9.0 | 9.3 | 9.6 |
| 13 | Documentation / DX | 9.3 | 9.4 | 9.5 | 9.6 | 9.7 | 9.8 | 9.85 | 9.9 |
| 14 | Stack modernity | 9.0 | 9.0 | 9.0 | 9.0 | 9.0 | 9.0 | 9.0 | 9.5 |

Wave 8 program mean ~9.8 — **отозвано** после аудита кода 2026-07-08.

</details>

---

## Процесс обновления

1. После каждого release tag — повторный **аудит кода**: матрица drivers, grep stub, CI gates, integration tests, датированные `docs/evidence/`.
2. Обновить столбец **Проверено по коду** здесь **и** в EN-каноне; delta в release notes / History roadmap.
3. Program wave columns — только shipment BL, **не** замена code verified.
4. Связывать доказательства с тестами, путями в `packages/` и evidence JSON/MD.
5. Lab PASS из [parked-backlog](parked-backlog.md) входят в scorecard только на **именованном полном audit**.
6. После правок матрицы: `python tools/ai-pack/build.py` (обновить `competitiveGapIndex`).
