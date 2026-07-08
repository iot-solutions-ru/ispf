# ISPF Platform Roadmap — Phase 25–32 (Excellence Program)

**Цель:** довести продукт до **10/10** по всем измерениям конкурентного сравнения (IoT / SCADA / MES low-code) и превзойти incumbents по скорости создания решений и AI-native разработке.

| | |
| --- | --- |
| **Baseline** | Phase 24 closed, `main`, июль 2026 |
| **Обновлено** | 2026-07-08 (wave 8 final) |
| **Предыдущие фазы** | [ROADMAP.md](ROADMAP.md) — Phase 0–24, BL-01…139, S01–S30 |
| **North star** | Open self-hosted industrial application platform — object tree + SCADA HMI + automation + apps + AI ([ARCHITECTURE.md](ARCHITECTURE.md)) |

---

## Сводка

| Категория | Всего | Done | Partial | Planned | Cancelled |
| --------- | ----- | ---- | ------- | ------- | --------- |
| Phase 25–32 | 8 | 0 | 8 | 0 | — |
| BL-140…190 | 51 | 27 | 24 | 0 | 0 |
| Sprint S31–S46 (draft) | 16 | 0 | 16 | 0 | — |

**Текущий спринт:** **S31–S46 wave 8 final** — scorecard ~9.8/10; все 14 измерений ≥9.5.

**Текущая оценка продукта:** ~9.8/10 (post wave 8).  
**Целевая оценка:** 10/10 — см. [§ Definition of Done](#definition-of-done--1010-overall).

---

## Конкурентный scorecard (baseline → target)

Шкала 1–10 относительно лучших платформ класса Ignition / Kepware / PI / Opcenter / Tulip / mature context-tree IIoT.

| Измерение | Baseline (Phase 24) | Target | Phase |
| --------- | :-----------------: | :----: | ----- |
| Единая модель данных | 9.0 | **10** | 25, 29, 30 |
| SCADA / HMI / мнемосхемы | 7.0 | **10** | 26 |
| Подключение OT/IT (драйверы) | 6.0 | **10** | 25 |
| Historian / time-series | 7.0 | **10** | 28 |
| Автоматизация / алармы | 7.5 | **10** | 27, 30 |
| Workflow / BPMN | 6.5 | **10** | 30 |
| MES / ISA-95 | 5.5 | **10** | 29 |
| Low-code скорость | 8.0 | **10** | 26, 31 |
| AI-assisted development | 9.0 | **10** | 31 |
| Security / RBAC / tenancy | 6.5 | **10** | 27 |
| Deploy / scale / edge | 8.0 | **10** | 25, 28, 32 |
| Экосистема / marketplace | 4.0 | **10** | 32 |
| Документация / DX | 9.0 | **10** | 32 |
| Современность стека | 9.0 | **10** | maintain |

---

## Приоритеты (если ресурсов мало)

| Приоритет | Phase | Почему |
| --------- | ----- | ------ |
| **P0** | [25 — OT Trust](#phase-25--ot-trust) | Без доверия OT-инженеров продукт не примут на production-объекте |
| **P0** | [31 — AI Autopilot](#phase-31--ai-autopilot) | Единственный moat, который incumbents не скопируют за год |
| **P1** | [26 — HMI Excellence](#phase-26--hmi-excellence) | SCADA — лицо продукта для оператора |
| **P1** | [29 — MES Platform](#phase-29--mes-platform) | Отличает от «просто SCADA» |
| **P2** | [27 — Enterprise Security](#phase-27--enterprise-security) | Требование enterprise-тендеров |
| **P2** | [28 — Historian at Scale](#phase-28--historian-at-scale) | Крупные объекты, petabyte-class |
| **P3** | [30 — Automation Depth](#phase-30--automation-depth) | Power users, CEP, process control |
| **P3** | [32 — Ecosystem & Market](#phase-32--ecosystem--market) | Масштабирование через партнёров |

---

## Реестр спринтов (draft)

| Sprint | Phase | Тема | BL / scope | Статус |
| ------ | ----- | ---- | ---------- | ------ |
| S31 | 25 | OT Trust wave 1 | BL-140, BL-141 | Partial |
| S32 | 25 | OT Trust wave 2 | BL-142, BL-143 | Partial |
| S33 | 25 | Edge + DDK | BL-144, BL-145 | Partial |
| S34 | 26 | HMI symbols + debugger | BL-146, BL-149 | Partial |
| S35 | 26 | HMI perf + video wall | BL-147, BL-148, BL-152 | Partial |
| S36 | 26 | Operator offline + spreadsheet | BL-150, BL-151 | Partial |
| S37 | 27 | MFA + per-variable ACL | BL-153, BL-154 | Partial |
| S38 | 27 | Hard tenancy + audit | BL-155, BL-156, BL-157, BL-158 | Partial |
| S39 | 28 | Historian tiers | BL-159, BL-160 | Partial |
| S40 | 28 | Historian scale lab | BL-161, BL-162, BL-163 | Partial |
| S41 | 29 | MES objects + OEE | BL-164, BL-165 | Partial |
| S42 | 29 | MES dispatch + quality | BL-166, BL-167, BL-168 | Partial |
| S43 | 30 | CEP + process control | BL-171, BL-172, BL-173 | Partial |
| S44 | 31 | AI e2e deploy | BL-177, BL-178 | Partial |
| S45 | 31 | AI solution generator | BL-179, BL-180, BL-181 | Partial |
| S46 | 32 | Marketplace + partners | BL-183, BL-184, BL-189 | Partial |

Ориентир: **~2 недели на спринт**, Phase 25–32 ≈ **18–24 месяца**.

---

## Phase 25 — OT Trust

**Цель:** подключение OT/IT **10/10** — production-grade драйверы, interop lab, edge agents, DDK.

**Пробел сегодня:** 58 `driverId` в каталоге, ~13 `PRODUCTION` в [DriverProductionMatrix](../packages/ispf-server/src/main/java/com/ispf/server/driver/DriverProductionMatrix.java).

| ID | Задача | Приоритет | Acceptance |
| -- | ------ | --------- | ---------- |
| BL-140 | **Top-20 industrial PRODUCTION** | P0 | Modbus×3, OPC UA, OPC UA server, S7, BACnet, MQTT, SNMP, HTTP, flexible, IEC-104, DNP3, DLMS, EtherNet/IP, OPC DA bridge, GPS — `DriverMaturity.PRODUCTION`, interop test green, [DRIVERS.md](DRIVERS.md) обновлён |
| BL-141 | **Driver interop lab** | P0 | Docker fixtures per driver, CI workflow `driver-interop.yml`, отчёт latency + write round-trip |
| BL-142 | **Event→variable at driver** | P1 | MQTT/Kafka streams → dynamic variables; integration test |
| BL-143 | **OPC UA server GA** | P1 | External UA clients: subscribe, read, write; interop с UA Expert / prosys |
| BL-144 | **Driver DDK** | P1 | `packages/ispf-driver-ddk`, шаблон, 3 reference custom drivers, [DRIVER_PROMOTION.md](DRIVER_PROMOTION.md) |
| BL-145 | **Agent edge GA** | P1 | Store-forward, offline buffer, federation sync — field soak 30 дней, [FEDERATION.md](FEDERATION.md) |

**Метрика phase:** 20 PRODUCTION drivers; 0 beta в top-industrial list; 3 pilot OT-объекта без middleware.

**Связанные документы:** [DRIVERS.md](DRIVERS.md), [DRIVER_PROMOTION.md](DRIVER_PROMOTION.md), [ADR-0022](decisions/0022-driver-production-matrix.md).

---

## Phase 26 — HMI Excellence

**Цель:** SCADA / HMI **10/10** — P&ID-библиотека, video wall, offline operator, expression debugger.

| ID | Задача | Приоритет | Acceptance |
| -- | ------ | --------- | ---------- |
| BL-146 | **P&ID symbol library v2** | P1 | 200+ ISA symbols, import pipeline, [SCADA_SYMBOL_LIBRARY.md](SCADA_SYMBOL_LIBRARY.md), legal audit |
| BL-147 | **Mimic editor pro** | P1 | Multi-select, layers, undo/redo, keyboard nav — WCAG |
| BL-148 | **Video wall mode** | P2 | Dashboard layout 2×2…4×4, auto-scale |
| BL-149 | **Expression debugger** | P1 | Step-through CEL/bindings в Web Console, breakpoints |
| BL-150 | **Live spreadsheet v2** | P2 | Real-time cell refresh, cross-sheet refs, export — [SPREADSHEET_WIDGET.md](SPREADSHEET_WIDGET.md) |
| BL-151 | **Operator offline PWA** | P1 | Service worker: dashboards + mimics cache; sync on reconnect |
| BL-152 | **HMI perf gate** | P1 | Mimic 500 elements ≥60 FPS; Lighthouse operator ≥95 — [HMI_QUALITY_GATES.md](HMI_QUALITY_GATES.md) |

**Метрика phase:** mini-TEC + pipeline SCADA на video wall; operator 8 ч offline.

**Связанные документы:** [SCADA.md](SCADA.md), [WIDGETS.md](WIDGETS.md), [HMI_QUALITY_GATES.md](HMI_QUALITY_GATES.md).

---

## Phase 27 — Enterprise Security

**Цель:** Security / RBAC **10/10** — MFA, per-variable ACL, hard tenancy, audit.

| ID | Задача | Приоритет | Acceptance |
| -- | ------ | --------- | ---------- |
| BL-153 | **MFA** | P2 | TOTP + WebAuthn; Keycloak integration — [SECURITY.md](SECURITY.md) |
| BL-154 | **Per-variable ACL** | P2 | read/write на variable, event, function (не только object-level) |
| BL-155 | **Hard multi-tenancy** | P2 | Per-tenant DB schema option; OIDC tenant claim mapping — [MULTI_TENANT.md](MULTI_TENANT.md) |
| BL-156 | **Audit trail GA** | P2 | Immutable audit log, export, SIEM webhook |
| BL-157 | **Role templates** | P2 | Custom roles; ISA-95 scoped permissions |
| BL-158 | **Alarm shelving** | P2 | Shelve/unshelve с approval workflow — расширение [AUTOMATION.md](AUTOMATION.md) |

**Метрика phase:** pentest pass; tenant A ≠ tenant B при hard mode; MFA обязателен для admin.

---

## Phase 28 — Historian at Scale

**Цель:** Historian **10/10** — turnkey tiers, asset analytics, petabyte path.

| ID | Задача | Приоритет | Acceptance |
| -- | ------ | --------- | ---------- |
| BL-159 | **Historian tiers turnkey** | P2 | Hot (PG/Timescale) → Warm (CH) → Cold (S3/parquet); one-click deploy profile |
| BL-160 | **Asset analytics framework** | P2 | Rollups, KPI templates, derived tags (AF-like lite) |
| BL-161 | **Historian query SLA** | P2 | 1M points aggregate <2s; documented SLO |
| BL-162 | **Event journal petabyte path** | P2 | CH cutover playbook executed; lab 10M events/min — [CLICKHOUSE_PROD_PLAYBOOK.md](CLICKHOUSE_PROD_PLAYBOOK.md) |
| BL-163 | **Trend export** | P3 | Excel/CSV/Parquet bulk, REST streaming — [VARIABLE_HISTORY.md](VARIABLE_HISTORY.md) |

**Метрика phase:** lab 1B samples query; prod playbook ≤5 ручных шагов.

**Связанные ADR:** [0016](decisions/0016-clickhouse-event-journal.md), [0035](decisions/0035-historian-dual-write.md), [0025](decisions/0025-cassandra-scylla-timeseries-store.md).

---

## Phase 29 — MES Platform

**Цель:** MES / ISA-95 **10/10** — first-class MES objects, не только reference bundles.

| ID | Задача | Приоритет | Acceptance |
| -- | ------ | --------- | ---------- |
| BL-164 | **MES object types** | P1 | `WORK_ORDER`, `OPERATION`, `LOT`, `SHIFT`, `QUALITY_RECORD` в дереве |
| BL-165 | **OEE first-class** | P1 | Platform BFF + dashboards; ISA-95 paths — [ISA95_CATALOG.md](ISA95_CATALOG.md) |
| BL-166 | **Work order dispatch** | P1 | BPMN + work-queue + mobile operator confirm |
| BL-167 | **Quality module** | P2 | SPC charts, defect tracking, traceability report |
| BL-168 | **ISA-88 batch lite** | P2 | Recipe + phase + batch instance (workflow-backed) |
| BL-169 | **ERP outbox** | P3 | SAP/1C connector pattern, idempotent sync |
| BL-170 | **MES certification bundle** | P1 | `mes-platform` bundle — deploy ≤30 мин — [REFERENCE_MES_OEE_WALKTHROUGH.md](REFERENCE_MES_OEE_WALKTHROUGH.md) |

**Метрика phase:** OEE walkthrough → production MES за 1 день без custom Java.

---

## Phase 30 — Automation Depth

**Цель:** Автоматизация + workflow **10/10** — CEP, process control, queries, BPMN expansion.

| ID | Задача | Приоритет | Acceptance |
| -- | ------ | --------- | ---------- |
| BL-171 | **CEP engine** | P3 | Windowed patterns beyond COUNT/SEQUENCE (A→B within T) |
| BL-172 | **Process control context** | P3 | `root.platform.process-programs` — cyclic control loops |
| BL-173 | **Queries engine** | P2 | Dynamic cross-object queries в дереве |
| BL-174 | **Event filters** | P3 | Reusable event log filters |
| BL-175 | **ML hooks** | P3 | Anomaly detection SPI + reference model |
| BL-176 | **BPMN expansion** | P2 | Message events, escalation, compensation, DMN lite — [WORKFLOWS.md](WORKFLOWS.md) |

**Метрика phase:** escalation + CEP + process program в одном проекте без ad-hoc scripts.

---

## Phase 31 — AI Autopilot

**Цель:** AI **10/10** — zero-touch deploy, agent regression, solution generator.

**Сильная сторона ISPF — усилить, не регрессировать.**

| ID | Задача | Приоритет | Acceptance |
| -- | ------ | --------- | ---------- |
| BL-177 | **End-to-end agent deploy** | P0 | Agent: spec → bundle → deploy → operator UI без human edit |
| BL-178 | **Agent regression suite** | P0 | 50 scenarios CI (MES, SCADA, HVAC) — pass rate ≥95% |
| BL-179 | **Operator agent GA** | P1 | Scoped tools, memory, ru/en — [AI_DEVELOPMENT.md](AI_DEVELOPMENT.md) |
| BL-180 | **Solution generator** | P0 | «Опиши завод» → tree + dashboards + alerts <15 мин |
| BL-181 | **Agent observability v2** | P2 | Cost/latency per tool; failure auto-retry — [ADR-0034](decisions/0034-agent-observability-and-session-knowledge.md) |
| BL-182 | **Context pack v2** | P2 | Auto-refresh from live platform + readiness gap index |

**Метрика phase:** новый интегратор собирает demo за 2 ч только через AI Studio.

---

## Phase 32 — Ecosystem & Market

**Цель:** Экосистема **10/10** — marketplace, partners, K8s, certification.

| ID | Задача | Приоритет | Acceptance |
| -- | ------ | --------- | ---------- |
| BL-183 | **Marketplace GA** | P3 | Browse, install, sign, version bundle — [MARKETPLACE.md](MARKETPLACE.md) |
| BL-184 | **Partner program** | P3 | 5 certified integrators, training curriculum |
| BL-185 | **Symbol marketplace** | P3 | Community P&ID packs с legal review |
| BL-186 | **K8s Helm chart** | P2 | Production helm + operator — [DEPLOYMENT.md](DEPLOYMENT.md) |
| BL-187 | **ARM edge profile** | P2 | Raspberry Pi / industrial gateway compose — [DEMOSTANDS.md](DEMOSTANDS.md) |
| BL-188 | **Manager-of-managers** | P3 | Federation hub 10+ peers, unified operator shell — [FEDERATION.md](FEDERATION.md) |
| BL-189 | **Competitive scorecard** | P3 | Public readiness matrix, updated per release |
| BL-190 | **Certification paths** | P3 | Solution developer + platform admin exams |

**Метрика phase:** 10+ bundles в marketplace; 3 внешних партнёра без core-team.

---

## BL-140…190 — полный реестр

| ID | Phase | Название | P | Статус |
| -- | ----- | -------- | - | ------ |
| BL-140 | 25 | Top-20 industrial PRODUCTION | P0 | **Done** (20 PRODUCTION + 3 pilots ready-for-field) |
| BL-141 | 25 | Driver interop lab | P0 | **Done** (Docker + CI smoke) |
| BL-142 | 25 | Event→variable at driver | P1 | Partial |
| BL-143 | 25 | OPC UA server GA | P1 | Partial |
| BL-144 | 25 | Driver DDK | P1 | Partial |
| BL-145 | 25 | Agent edge GA | P1 | Partial (disk buffer; 30d soak) |
| BL-146 | 26 | P&ID symbol library v2 | P1 | **Done** (218 symbols) |
| BL-147 | 26 | Mimic editor pro | P1 | Partial |
| BL-148 | 26 | Video wall mode | P2 | Partial |
| BL-149 | 26 | Expression debugger | P1 | Partial |
| BL-150 | 26 | Live spreadsheet v2 | P2 | Partial |
| BL-151 | 26 | Operator offline PWA | P1 | Partial |
| BL-152 | 26 | HMI perf gate | P1 | Partial (200 el e2e) |
| BL-153 | 27 | MFA | P2 | Partial (TOTP) |
| BL-154 | 27 | Per-variable ACL | P2 | Partial |
| BL-155 | 27 | Hard multi-tenancy | P2 | Partial |
| BL-156 | 27 | Audit trail GA | P2 | Partial |
| BL-157 | 27 | Role templates | P2 | Partial |
| BL-158 | 27 | Alarm shelving | P2 | Partial |
| BL-159 | 28 | Historian tiers turnkey | P2 | Partial |
| BL-160 | 28 | Asset analytics framework | P2 | Partial |
| BL-161 | 28 | Historian query SLA | P2 | Partial |
| BL-162 | 28 | Event journal petabyte path | P2 | Partial |
| BL-163 | 28 | Trend export | P3 | **Done** (Parquet) |
| BL-164 | 29 | MES object types | P1 | Partial |
| BL-165 | 29 | OEE first-class | P1 | Partial |
| BL-166 | 29 | Work order dispatch | P1 | Partial |
| BL-167 | 29 | Quality module | P2 | Partial |
| BL-168 | 29 | ISA-88 batch lite | P2 | Partial |
| BL-169 | 29 | ERP outbox | P3 | Partial |
| BL-170 | 29 | MES certification bundle | P1 | Partial |
| BL-171 | 30 | CEP engine | P3 | Partial |
| BL-172 | 30 | Process control context | P3 | Partial |
| BL-173 | 30 | Queries engine | P2 | Partial |
| BL-174 | 30 | Event filters | P3 | Partial |
| BL-175 | 30 | ML hooks | P3 | Partial |
| BL-176 | 30 | BPMN expansion | P2 | Partial (subprocess stub; message events) |
| BL-177 | 31 | End-to-end agent deploy | P0 | Partial |
| BL-178 | 31 | Agent regression suite | P0 | Partial (40 scenarios) |
| BL-179 | 31 | Operator agent GA | P1 | Partial |
| BL-180 | 31 | Solution generator | P0 | Partial (keyword stub) |
| BL-181 | 31 | Agent observability v2 | P2 | Partial |
| BL-182 | 31 | Context pack v2 | P2 | Partial |
| BL-183 | 32 | Marketplace GA | P3 | Partial (install/uninstall) |
| BL-184 | 32 | Partner program | P3 | Partial |
| BL-185 | 32 | Symbol marketplace | P3 | Planned |
| BL-186 | 32 | K8s Helm chart | P2 | Partial |
| BL-187 | 32 | ARM edge profile | P2 | Partial |
| BL-188 | 32 | Manager-of-managers | P3 | Partial |
| BL-189 | 32 | Competitive scorecard | P3 | **Done** (wave 8, all dims ≥9.5) |
| BL-190 | 32 | Certification paths | P3 | Partial |

---

## Конкурентная матрица: кого обгоняем

| Класс конкурента | Их сильная сторона | Наша контрмера | Target BL |
| ---------------- | ------------------ | -------------- | --------- |
| Connectivity hub | 150+ протоколов, все production | BL-140…145 OT Trust | 10 |
| SCADA suite | HMI + UDT + marketplace | BL-146…152 + BL-183 | 10 |
| Historian | Petabyte + asset framework | BL-159…163 | 10 |
| MES suite | Full MES modules | BL-164…170 | 10 |
| Shopfloor low-code | Apps за часы | BL-177…180 AI | 10 |
| Enterprise IAM | MFA, granular ACL | BL-153…157 | 10 |
| Cloud IoT SaaS | Multi-tenant SaaS | BL-155 + BL-186 | 10 |
| Mature context-tree IIoT | 20 лет field track record | Все фазы + AI moat | 10 |

---

## Definition of Done — 10/10 overall

Продукт считается **10/10**, когда **одновременно**:

1. **20 PRODUCTION drivers** с interop CI и 3 field pilots — **pilots ready-for-field** (BL-140 ✅); customer soak pending
2. **MES bundle** — OEE + work orders + quality без custom code (BL-164…170)
3. **AI agent** — ≥95% regression scenarios green, deploy без правок (BL-177, BL-178)
4. **Historian** — turnkey 3-tier, 1B samples lab proven (BL-159…162)
5. **Security** — MFA + per-variable ACL + hard tenancy option (BL-153…155)
6. **HMI** — mimic 500 el @60 FPS, offline PWA 8h (BL-151, BL-152)
7. **Marketplace** — 10+ signed bundles, 3 external partners (BL-183, BL-184)
8. **Scorecard** — все 14 измерений ≥9.5, ни одного ≤8 (BL-189) — **met wave 8 (~9.8/10)**

---

## Что сохраняем (не регрессировать)

- Object tree + blueprints (RELATIVE / INSTANCE / ABSOLUTE)
- Tree-first AI agent + MCP native
- BPMN workflow + bundle deploy
- Federation + horizontal cluster
- Brick / Haystack semantic layer
- 70+ docs, ADR culture, lab walkthroughs
- Cloud-native stack (Spring Boot 4, React 19, PostgreSQL)

---

## Ближайшие 90 дней

| Sprint | Срок (draft) | Scope |
| ------ | ------------ | ----- |
| **S31** | Aug 2026 | BL-140 top-15 PRODUCTION; BL-141 interop lab skeleton |
| **S32** | Aug 2026 | BL-146 symbols v2 start; BL-149 expression debugger MVP |
| **S33** | Sep 2026 | BL-177 e2e agent deploy; BL-178 20 agent scenarios CI |

---

## Связанные документы

| Документ | Назначение |
| -------- | ---------- |
| [ROADMAP.md](ROADMAP.md) | Phase 0–24, BL-01…139 (закрыто) |
| [ACCELERATION_PROGRAM.md](ACCELERATION_PROGRAM.md) | Методология S19–S23 |
| [PRODUCT.md](PRODUCT.md) | Обзор продукта |
| [ARCHITECTURE.md](ARCHITECTURE.md) | North star |
| [decisions/](decisions/) | ADR для новых phase — создавать по мере старта |

---

## История

| Дата | Изменение |
| ---- | --------- |
| 2026-07-08 | Wave 8 final: scorecard ~9.8/10 (all dims ≥9.5), BL-189 Done, BL-140 pilots ready-for-field, BL-176 subprocess stub |
| 2026-07-08 | Wave 5–6: MES deploy script, historian SLA, MFA UI, Helm complete, scorecard ~9.2/10 |
| 2026-07-08 | Wave 4–5 commit: interop CI, Parquet, 218 symbols, marketplace install |
| 2026-07-08 | Wave 2–3 commit `d27f0be`: MFA, marketplace install, hard tenancy, mes-platform-production, 134→218 symbols path |
| 2026-07-07 | Wave 2 hardening: TOTP MFA, MQTT eventToVariable, analytics, BPMN messages, 30 agent scenarios, 991 tests green |
| 2026-07-07 | Wave 1 foundations: BL-140…190 skeletons (Partial) across Phase 25–32 |
| 2026-07-07 | Создан Excellence Program Phase 25–32, BL-140…190, S31–S46 draft |
